import geopandas as gpd
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
import numpy as np
from matplotlib import ticker
plt.style.use("default")

""" 1. data preperation """
# Hamburg shp. epsg:25832. Nanjing shp:32650

# baseline scenario
# TODO: file path. for hamburg
# link_baseline_path = "Your folder path\\hamburg-v3.0-network-with-pt.shp"
# out_failure_map_path = "Your folder path\\hamburg "
# coupled_net_baseline_gdf = gpd.read_file(link_baseline_path)
# coupled_net_baseline_gdf.to_crs(epsg=4326, inplace=True)
# print("Allowed modes for link mode for coupled net in Hamburg: ", list(coupled_net_baseline_gdf.modes.drop_duplicates()))

# for los angeles This is 
# link_baseline_path = r"Your folder path\\my2-los-angeles-v1.0-network.shp"
# out_failure_map_path = "Your folder path\\my2-50th-los angeles "
# coupled_net_baseline_gdf = gpd.read_file(link_baseline_path)
# coupled_net_baseline_gdf.to_crs(epsg=4326, inplace=True)
# print("Allowed modes for link mode for coupled net in Los Angeles: ", list(coupled_net_baseline_gdf.modes.drop_duplicates()))

# for nanjing. NOTE: coupled_net is for the failure map of road network. pt_network is for the failure map of pt network.
coupled_net_baseline_path = "Your folder path\\coupled network.shp"
pt_baseline_path = "Your folder path\\nanjingBasePtNetwork.shp"
out_failure_map_path = "Your folder path\\nanjing "

coupled_net_baseline_gdf = gpd.read_file(coupled_net_baseline_path)
coupled_net_baseline_gdf.to_crs(epsg=4326, inplace=True)
print("Allowed modes for link mode for coupled net in Nanjing: ", list(coupled_net_baseline_gdf.modes.drop_duplicates()))

pt_baseline_gdf = gpd.read_file(pt_baseline_path)
pt_baseline_gdf.to_crs(epsg=4326, inplace=True)
print("Allowed modes for link mode for pt network in Nanjing: ", list(pt_baseline_gdf.modes.drop_duplicates()))

# plot for nanjing scenario.
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'

def plot_failure_map(threshold, direct_failed_road_linkId_set, direct_failed_pt_linkId_set, indirect_failed_road_linkId_set, indirect_failed_pt_linkId_set, road_linkId_set_after_operation, pt_linkId_set_after_operation):
    print("............Begin plot failure map, inundation threshold = ", str(threshold), ".............")
    # 1. plot the road network
    fig = plt.figure()
    ax = fig.add_subplot(111)
    plt.xticks(fontsize=12)
    plt.yticks(fontsize=12)
    # open links
    gdf = coupled_net_baseline_gdf[coupled_net_baseline_gdf['ID'].isin(road_linkId_set_after_operation)]
    if len(gdf) > 0:
        gdf.plot(ax=ax, color="#4daf4a", label="Open link")
    else:
        print("Road network has been destroied totally")
    # indirect failed links
    gdf = coupled_net_baseline_gdf[coupled_net_baseline_gdf['ID'].isin(indirect_failed_road_linkId_set)]
    if len(gdf) > 0:
        gdf.plot(ax=ax, color="#e41a1c", label="Indirect failed link")
    else:
        print("Road network has no indirect failures")
    # direct failed links
    gdf = coupled_net_baseline_gdf[coupled_net_baseline_gdf['ID'].isin(direct_failed_road_linkId_set)]
    if len(gdf) > 0:
        gdf.plot(ax=ax, color="#377eb8", label="Direct failed link")
    else:
        print("Road network has no direct failures")
    plt.legend(loc="lower left", bbox_to_anchor=(1.05,0.15), fontsize=12, frameon=False)
    plt.savefig(out_failure_map_path + "link map for road network-threshold" + str(threshold) + ".pdf", format='pdf', pad_inches=0.1, bbox_inches='tight')

    # 2. plot the pt network
    fig = plt.figure()
    ax = fig.add_subplot(111)
    plt.xticks(fontsize=12)
    plt.yticks(fontsize=12)
    # open links
    gdf = pt_baseline_gdf[pt_baseline_gdf['ID'].isin(pt_linkId_set_after_operation)]
    if len(gdf) > 0:
        gdf.plot(ax=ax, color="#4daf4a", label="Open link") 
    else:
        print("Pt network has been destroied totally.")
    # indirect failed links
    gdf = pt_baseline_gdf[pt_baseline_gdf['ID'].isin(indirect_failed_pt_linkId_set)]
    if len(gdf) > 0:
        gdf.plot(ax=ax, color="#e41a1c", label="Indirect failed link")
    else:
        print("Pt network has no indirect failure.")
    # direct failed links
    gdf = pt_baseline_gdf[pt_baseline_gdf['ID'].isin(direct_failed_pt_linkId_set)]
    if len(gdf) > 0:
        gdf.plot(ax=ax, color="#377eb8", label="Direct failed link")
    else:
        print("Pt network has no direct failure.")
    plt.legend(loc="lower left", bbox_to_anchor=(1.05,0.15), fontsize=12, frameon=False)
    plt.savefig(out_failure_map_path + "link map for pt network-threshold" + str(threshold) + ".pdf", format='pdf', pad_inches=0.1, bbox_inches='tight')



# flooding scenario for nanjing scenario
#NOTE: In nanjing scenario, bus network is on the road network. We extract road network from coupled net and extract pt network for pt network.
pt_linkId_set, road_linkId_set = set(), set()
for i in range(len(coupled_net_baseline_gdf)):
    # NOTE: Whatch out the modes of the link maybe "[car]", "[bus, car]" or "[car, bus]". This is randomly generated (Chunhong'230823)
    if ((coupled_net_baseline_gdf['modes'].iloc[i] == "[car]") or (coupled_net_baseline_gdf['modes'].iloc[i] == "[bus, car]") or (coupled_net_baseline_gdf['modes'].iloc[i] == "[car, bus]")):
        road_linkId_set.add(coupled_net_baseline_gdf['ID'].iloc[i])

for i in range(len(pt_baseline_gdf)):
    pt_linkId_set.add(pt_baseline_gdf['ID'].iloc[i])

threshold_list = [5.2, 5.4, 5.6, 5.8]
for threshold in threshold_list:
    # after direct failures
    coupled_link_direct_failure_path = r"Your folder path\nanjing-network-direct failure-threshold" + str(threshold) + "m.shp"
    pt_link_direct_failure_path = r"Your folder path\nanjing-pt network-direct failure-threshold" + str(threshold) + "m.shp"
    # after indirect failures. Road network: GCC. Pt network: transit schedule operational failures.
    coupled_link_indirect_failure_path = r"Your folder path\nanjing-network-indirect failure-threshold" + str(threshold) + "m.shp"
    pt_link_operation_failure_path = r"Your folder path\nanjing-pt network-operational failure-threshold" + str(threshold) + "m.shp"

    coupled_link_direct_failure_gdf = gpd.read_file(coupled_link_direct_failure_path)
    pt_link_direct_failure_gdf = gpd.read_file(pt_link_direct_failure_path)
    coupled_link_indirect_failure_gdf = gpd.read_file(coupled_link_indirect_failure_path)
    pt_link_operation_failure_gdf = gpd.read_file(pt_link_operation_failure_path)

    # Count the links in the road network after direct failures
    road_linkId_set_after_direct = set()
    for i in range(len(coupled_link_direct_failure_gdf)):
        # NOTE: this is different from codes for hamburg.
        # NOTE: Whatch out the modes of the link maybe "[car]", "[bus, car]" or "[car, bus]". This is randomly generated
        if ((coupled_link_direct_failure_gdf['modes'].iloc[i] == "[car]") or (coupled_link_direct_failure_gdf['modes'].iloc[i] == "[bus, car]") or (coupled_link_direct_failure_gdf['modes'].iloc[i] == "[car, bus]")):
            road_linkId_set_after_direct.add(coupled_link_direct_failure_gdf['ID'].iloc[i])

    # Count the links in the road network after indirect failures.
    road_linkId_set_after_indirect = set()
    for i in range(len(coupled_link_indirect_failure_gdf)):
        if ((coupled_link_indirect_failure_gdf['modes'].iloc[i] == "[car]") or (coupled_link_indirect_failure_gdf['modes'].iloc[i] == "[bus, car]") or (coupled_link_indirect_failure_gdf['modes'].iloc[i] == "[car, bus]")):
            road_linkId_set_after_indirect.add(coupled_link_indirect_failure_gdf['ID'].iloc[i])
    
    # Count the links in the pt network after direct failures
    pt_linkId_set_after_direct = set()
    for i in range(len(pt_link_direct_failure_gdf)):
        pt_linkId_set_after_direct.add(pt_link_direct_failure_gdf['ID'].iloc[i])
    
    # Count the links in the pt network after schedule failures
    pt_linkId_set_after_operation = set()
    for i in range(len(pt_link_operation_failure_gdf)):
        pt_linkId_set_after_operation.add(pt_link_operation_failure_gdf['ID'].iloc[i])

    # identify the link, direct failed link and indirect failed link.
    direct_failed_road_linkId_set = road_linkId_set - road_linkId_set_after_direct
    direct_failed_pt_linkId_set = pt_linkId_set - pt_linkId_set_after_direct
    indirect_failed_road_linkId_set = road_linkId_set_after_direct - road_linkId_set_after_indirect
    indirect_failed_pt_linkId_set = pt_linkId_set_after_direct - pt_linkId_set_after_operation
    print("for road net: ")
    print("baseline: ", len(road_linkId_set))
    print("after direct failures: ", len(road_linkId_set_after_direct))
    print("after indirect failures: ", len(road_linkId_set_after_indirect))
    print()
    print("the number of direct failures: ", len(direct_failed_road_linkId_set))
    print("the number of indirect failures: ", len(indirect_failed_road_linkId_set))
    print("the number of open links: ", len(road_linkId_set_after_indirect))
    print("the number of all links: ", len(road_linkId_set))
    
    # plot the failure map.
    plot_failure_map(threshold, direct_failed_road_linkId_set, direct_failed_pt_linkId_set, indirect_failed_road_linkId_set, indirect_failed_pt_linkId_set, road_linkId_set_after_indirect, pt_linkId_set_after_operation)
    # threshold += 1.0


""" for hamburg, German """
# baseline scenario
base_pt_link_num4Hamburg = 25827  
base_road_link_num4Hamburg = 882276
flooded_link_df = pd.read_csv(r"Your folder path\network damage for hamburg.csv")
flooded_link_df = flooded_link_df[flooded_link_df['threshold (m)'] <= 15]
threshold_hamburg = flooded_link_df['threshold (m)']
# after direct failures
direct_failed_pt_frac4Hamburg = list((base_pt_link_num4Hamburg - flooded_link_df['[pt]after direct']) / base_pt_link_num4Hamburg)
direct_failed_road_frac4Hamburg = list((base_road_link_num4Hamburg - flooded_link_df['[commercial_Lkw-k, commercial_Lkw-m, car, commercial_Lfw, ride, commercial_Trans, bike, commercial_Pkw-Lfw, commercial_Lkw-g, commercial_PWV_IV]after direct']) / base_road_link_num4Hamburg)
direct_failed_superNet_frac4Hamburg = list((base_road_link_num4Hamburg + base_pt_link_num4Hamburg - flooded_link_df['[pt]after direct'] - flooded_link_df['[commercial_Lkw-k, commercial_Lkw-m, car, commercial_Lfw, ride, commercial_Trans, bike, commercial_Pkw-Lfw, commercial_Lkw-g, commercial_PWV_IV]after direct']) / (base_road_link_num4Hamburg + base_pt_link_num4Hamburg))
# after indirect failures. NOTE: Indirect failures for pt network is operational failures.
operational_pt_frac4Hamburg = list(flooded_link_df['[pt]after indirect'] / base_pt_link_num4Hamburg)
gcc_road_frac4Hamburg = list(flooded_link_df['[commercial_Lkw-k, commercial_Lkw-m, car, commercial_Lfw, ride, commercial_Trans, bike, commercial_Pkw-Lfw, commercial_Lkw-g, commercial_PWV_IV]after indirect'] / base_road_link_num4Hamburg)
gcc_superNet_frac4Hamburg = list((flooded_link_df['[pt]after indirect'] + flooded_link_df['[commercial_Lkw-k, commercial_Lkw-m, car, commercial_Lfw, ride, commercial_Trans, bike, commercial_Pkw-Lfw, commercial_Lkw-g, commercial_PWV_IV]after indirect']) / (base_road_link_num4Hamburg + base_pt_link_num4Hamburg))

""" for nanjing, China """
# baseline scenario
base_pt_link_num4Nanjing = 17350  
base_road_link_num4Nanjing = 17319
flooded_link_df = pd.read_csv(r"Your folder path\network damage for nanjing.csv")
flooded_link_df = flooded_link_df[flooded_link_df['threshold (m)'] <= 15]
threshold_nanjing = flooded_link_df['threshold (m)']
# the fraction of direct failures
direct_failed_pt_frac4Nanjing = (base_pt_link_num4Nanjing - flooded_link_df['pt link after direct']) / base_pt_link_num4Nanjing
direct_failed_road_frac4Nanjing = (base_road_link_num4Nanjing - flooded_link_df['road link after direct']) / base_road_link_num4Nanjing
# after indirect failures. NOTE: Indirect failures for pt network is operational failures.
operational_pt_frac4Nanjing = flooded_link_df['pt link after indirect'] / base_pt_link_num4Nanjing
gcc_road_frac4Nanjing = flooded_link_df['road link after indirect'] / base_road_link_num4Nanjing

""" for los angeles, USA """
# baseline scenario
base_pt_link_num4LA = 19827
base_road_link_num4LA = 684680
flooded_link_df = pd.read_csv(r"Your folder path\network damage for my2 50th los angeles.csv")
flooded_link_df = flooded_link_df[flooded_link_df['threshold (m)'] <= 15]
threshold_la = flooded_link_df['threshold (m)']
# after direct failures
direct_failed_pt_frac4LA = list((base_pt_link_num4LA - flooded_link_df['[pt]after direct']) / base_pt_link_num4LA)
direct_failed_road_frac4LA = list((base_road_link_num4LA - flooded_link_df['[ride_school_bus, ride_taxi, freight, ride, car]after direct']) / base_road_link_num4LA)
direct_failed_superNet_frac4LA = list((base_road_link_num4LA + base_pt_link_num4LA - flooded_link_df['[pt]after direct'] - flooded_link_df['[ride_school_bus, ride_taxi, freight, ride, car]after direct']) / (base_road_link_num4LA + base_pt_link_num4LA))
# after indirect failures. NOTE: Indirect failures for pt network is operational failures.
operational_pt_frac4LA = list(flooded_link_df['[pt]after indirect'] / base_pt_link_num4LA)
gcc_road_frac4LA = list(flooded_link_df['[ride_school_bus, ride_taxi, freight, ride, car]after indirect'] / base_road_link_num4LA)
gcc_superNet_frac4LA = list((flooded_link_df['[pt]after indirect'] + flooded_link_df['[ride_school_bus, ride_taxi, freight, ride, car]after indirect']) / (base_road_link_num4LA + base_pt_link_num4LA))

""" figure: threshold versus the fraction of gcc"""
########### 1.1 only for road network 
plt.style.use("default")
# sns.set(font="Arial", font_scale=1.5, style="ticks")
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)
plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in') 
ax.tick_params(which ='minor', direction='in')
plt.xticks(fontsize=20)
plt.yticks(fontsize=20)

# for nanjing 
# the giant component for road network
ax.plot(threshold_nanjing, gcc_road_frac4Nanjing, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#d73027', linewidth=1, label='Nanjing Car')
# the residual link for pt network 
ax.plot(threshold_nanjing, operational_pt_frac4Nanjing, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#d73027', linewidth=1, label='Nanjing PT')

# for hamburg
ax.plot(threshold_hamburg, gcc_road_frac4Hamburg, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#4575b4', linewidth=1, label='Hamburg Car')
ax.plot(threshold_hamburg, operational_pt_frac4Hamburg, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#4575b4', linewidth=1, label='Hamburg PT')

# for LA
ax.plot(threshold_la, gcc_road_frac4LA, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#4daf4a', linewidth=1, label='LA Car')
ax.plot(threshold_la, operational_pt_frac4LA, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#4daf4a', linewidth=1, label='LA PT')

ax.set_yticks([0,0.2,0.4,0.6,0.8,1.0])
plt.xticks(fontsize=20)
plt.yticks(fontsize=20)
ax.set_xlabel("$T $ (m)", fontsize=20)
ax.set_ylabel("$P_{\infty}$", fontsize=20)
ax.xaxis.set_minor_locator(ticker.AutoMinorLocator(2))
ax.yaxis.set_minor_locator(ticker.AutoMinorLocator(2))

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
    
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.5,0.5), frameon=False, fontsize=20)
plt.savefig(r"Your output file path", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')

""" figure: fraction of direct failures versus fraction of gcc"""
########### 1.1 only for road network 
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)

plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in')
ax.tick_params(which ='minor', direction='in') 
plt.xticks(fontsize=20)
plt.yticks(fontsize=20)

# for nanjing 
# the giant component for road network
ax.plot(direct_failed_road_frac4Nanjing, gcc_road_frac4Nanjing, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#d73027', linewidth=1, label='Nanjing Car')
# the residual link for pt network 
ax.plot(direct_failed_pt_frac4Nanjing, operational_pt_frac4Nanjing, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#d73027', linewidth=1, label='Nanjing PT')

# for hamburg
ax.plot(direct_failed_road_frac4Hamburg, gcc_road_frac4Hamburg, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#4575b4', linewidth=1, label='Hamburg Car')
ax.plot(direct_failed_pt_frac4Hamburg, operational_pt_frac4Hamburg, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#4575b4', linewidth=1, label='Hamburg PT')

# for LA
ax.plot(direct_failed_road_frac4LA, gcc_road_frac4LA, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#4daf4a', linewidth=1, label='LA Car')
ax.plot(direct_failed_pt_frac4LA, operational_pt_frac4LA, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#4daf4a', linewidth=1, label='LA PT')

ax.set_yticks([0,0.2,0.4,0.6,0.8,1.0])
ax.set_xlabel("$ f $", fontsize=20)
ax.set_ylabel("$P_{\infty}$", fontsize=20)
ax.xaxis.set_minor_locator(ticker.AutoMinorLocator(2))
ax.yaxis.set_minor_locator(ticker.AutoMinorLocator(2))

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)

fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.5,0.5), frameon=False, fontsize=20)
plt.savefig(r"Your output file path", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')

""" figure: fraction of direct failures versus inundation threshold """
########### 1.1 only for road network 
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)
plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in') 
ax.tick_params(which ='minor', direction='in')
plt.xticks(fontsize=20)
plt.yticks(fontsize=20)

# for nanjing 
# the giant component for road network
ax.plot(threshold_nanjing, direct_failed_road_frac4Nanjing, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#d73027', linewidth=1, label='Nanjing Car')
# the residual link for pt network 
ax.plot(threshold_nanjing, direct_failed_pt_frac4Nanjing, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#d73027', linewidth=1, label='Nanjing PT')

# for hamburg
ax.plot(threshold_hamburg, direct_failed_road_frac4Hamburg, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#4575b4', linewidth=1, label='Hamburg Car')
ax.plot(threshold_hamburg, direct_failed_pt_frac4Hamburg, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#4575b4', linewidth=1, label='Hamburg PT')

# for LA
ax.plot(threshold_la, direct_failed_road_frac4LA, \
    alpha=1, marker='o', fillstyle="none" , markersize=7, color='#4daf4a', linewidth=1, label='LA Car')
ax.plot(threshold_la, direct_failed_pt_frac4LA, \
    alpha=1, marker='s', fillstyle="none" , markersize=7, color='#4daf4a', linewidth=1, label='LA PT')

ax.set_yticks([0,0.2,0.4,0.6,0.8,1.0])
ax.set_xlabel("$T $ (m)", fontsize=20)
ax.set_ylabel("$f$", fontsize=20)
ax.xaxis.set_minor_locator(ticker.AutoMinorLocator(2))
ax.yaxis.set_minor_locator(ticker.AutoMinorLocator(2))

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)

fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.5,0.5), frameon=False, fontsize=20)
plt.savefig(r"Your output file path", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')