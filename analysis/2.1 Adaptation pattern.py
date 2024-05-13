from pyecharts import options as opts
from pyecharts.charts import Sankey
from pyecharts.render import make_snapshot
from snapshot_selenium import snapshot
import pandas as pd
import matplotlib.pyplot as plt
import geopandas as gpd

""" 0. import data """
trips_baseline = pd.read_csv(r"Your file folder for baseline sceanrio\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold0 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold1 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold2 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold3 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold4 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold5 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold6 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold7 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold8 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold9 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold10 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold11 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold12 = pd.read_csv(r"Your file folder for flooding scenario\output_trips_wgs84_cleaned.csv", sep=";")

trips_baseline['threshold'] = -1
trips_threshold0['threshold'] = 0
trips_threshold1['threshold'] = 1
trips_threshold2['threshold'] = 2
trips_threshold3['threshold'] = 3
trips_threshold4['threshold'] = 4
trips_threshold5['threshold'] = 5
trips_threshold6['threshold'] = 6
trips_threshold7['threshold'] = 7
trips_threshold8['threshold'] = 8
trips_threshold9['threshold'] = 9
trips_threshold10['threshold'] = 10
trips_threshold11['threshold'] = 11
trips_threshold12['threshold'] = 12

""" 1. from baseline scenario to flooding scenarios """
trips_baseline2threshold12 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold8.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold11 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold8.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold10 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold8.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold9 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold8.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold8 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold8.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold7 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold7.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold6 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold6.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold5 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold5.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold4 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold4.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold3 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold3.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold2 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold2.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold1 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold1.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")
trips_baseline2threshold0 = pd.merge(how="outer", left=trips_baseline.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), right=trips_threshold0.drop(columns=['Unnamed: 0', 'person', 'modes', 'start_x', 'start_y', 'end_x', 'end_y', 'start_lat', 'start_lon', 'end_lat', 'end_lon']), on="trip_id")

""" 2. trip flows statistics """
tripsTransAllResults = []
i = 0
for tripsA2B in [trips_baseline2threshold12, trips_baseline2threshold11, trips_baseline2threshold10, trips_baseline2threshold9, \
    trips_baseline2threshold8, trips_baseline2threshold7, trips_baseline2threshold6, trips_baseline2threshold5, trips_baseline2threshold4, 
    trips_baseline2threshold3, trips_baseline2threshold2, trips_baseline2threshold1, trips_baseline2threshold0]:
    tripsTransResultsforA2B = {}
    # interuption  ---> car
    tripsTransResultsforA2B['Interuption to car'] = len(tripsA2B[(tripsA2B['threshold_x'].isnull()) & (tripsA2B['my_modes_y'] == 'car')])
    # interuption  ---> pt
    tripsTransResultsforA2B['Interuption to pt'] = len(tripsA2B[(tripsA2B['threshold_x'].isnull()) & (tripsA2B['my_modes_y'] == 'pt')])

    # interuption  ---> interuption
    tripsTransResultsforA2B['Interuption to interuption'] = 0

    # car ---> interuption
    tripsTransResultsforA2B['Car to interuption'] = len(tripsA2B[(tripsA2B['threshold_y'].isnull()) & (tripsA2B['my_modes_x'] == 'car')])
    # pt ---> interuption
    tripsTransResultsforA2B['Pt to interuption'] = len(tripsA2B[(tripsA2B['threshold_y'].isnull()) & (tripsA2B['my_modes_x'] == 'pt')])
    # NOTE: interuption + car + pt --> interuption == interuption_new_scenario
    
    # there is no interuption in the following statistics. So delete interuption lines.
    tripsA2B = tripsA2B[(~(tripsA2B['threshold_y'].isnull())) & (~(tripsA2B['threshold_x'].isnull()))]
    # car  --->  pt
    tripsTransResultsforA2B['Car to pt'] = len(tripsA2B[(tripsA2B['my_modes_x'] == 'car') & (tripsA2B['my_modes_y'] == 'pt')])
    # car  --->  car (no path change)
    tripsTransResultsforA2B['Car to car no path change'] = len(tripsA2B[(tripsA2B['my_modes_x'] == 'car') & (tripsA2B['my_modes_y'] == 'car') & (tripsA2B['traveled_distance_x'] == tripsA2B['traveled_distance_y'])])
    # car ---> car (with path change)
    tripsTransResultsforA2B['Car to car with path change'] = len(tripsA2B[(tripsA2B['my_modes_x'] == 'car') & (tripsA2B['my_modes_y'] == 'car') & (tripsA2B['traveled_distance_x'] != tripsA2B['traveled_distance_y'])])
    # pt  ---> car 
    tripsTransResultsforA2B['Pt to car'] = len(tripsA2B[(tripsA2B['my_modes_x'] == 'pt') & (tripsA2B['my_modes_y'] == 'car')])
    # pt ---> pt (no path change)
    tripsTransResultsforA2B['Pt to pt no path change'] = len(tripsA2B[(tripsA2B['my_modes_x'] == 'pt') & (tripsA2B['my_modes_y'] == 'pt') & (tripsA2B['traveled_distance_x'] == tripsA2B['traveled_distance_y'])])
    # pt ---> pt (with path change)
    tripsTransResultsforA2B['Pt to pt with path change'] = len(tripsA2B[(tripsA2B['my_modes_x'] == 'pt') & (tripsA2B['my_modes_y'] == 'pt') & (tripsA2B['traveled_distance_x'] != tripsA2B['traveled_distance_y'])])
    
    tripsTransAllResults.append(tripsTransResultsforA2B)
    i += 1

""" 3. fig: car trips """
plt.style.use("default")
# relative fraction: links volumes / nodes values (car)
car2car_samePath_rel = [tripsTransResultsforA2B['Car to car no path change'] / (tripsTransResultsforA2B['Car to car no path change'] + tripsTransResultsforA2B['Car to car with path change'] + tripsTransResultsforA2B['Car to pt'] + tripsTransResultsforA2B['Car to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]
car2car_pathChange_rel = [tripsTransResultsforA2B['Car to car with path change'] / (tripsTransResultsforA2B['Car to car no path change'] + tripsTransResultsforA2B['Car to car with path change'] + tripsTransResultsforA2B['Car to pt'] + tripsTransResultsforA2B['Car to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]
car2pt_rel = [tripsTransResultsforA2B['Car to pt'] / (tripsTransResultsforA2B['Car to car no path change'] + tripsTransResultsforA2B['Car to car with path change'] + tripsTransResultsforA2B['Car to pt'] + tripsTransResultsforA2B['Car to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]
car2interruption_rel = [tripsTransResultsforA2B['Car to interuption'] / (tripsTransResultsforA2B['Car to car no path change'] + tripsTransResultsforA2B['Car to car with path change'] + tripsTransResultsforA2B['Car to pt'] + tripsTransResultsforA2B['Car to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]

# direct failures for road and pt network
# baseline scenario
base_pt_link_num4Nanjing = 17350  
base_road_link_num4Nanjing = 17319
flooded_link_df = pd.read_csv(r"Your output file folder\network damage for nanjing.csv")
# the fraction of direct failures
direct_failed_pt_frac4Nanjing = list((base_pt_link_num4Nanjing - flooded_link_df['pt link after direct']) / base_pt_link_num4Nanjing)[:len(car2car_samePath_rel)]
direct_failed_road_frac4Nanjing = list((base_road_link_num4Nanjing - flooded_link_df['road link after direct']) / base_road_link_num4Nanjing)[:len(car2car_samePath_rel)]
direct_failed_pt_frac4Nanjing.reverse() # Destruction ratio from small to large, inundation threshold from large to small
direct_failed_road_frac4Nanjing.reverse() 

from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)
plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in')
ax.tick_params(which ='minor', direction='in')
plt.xticks(fontsize=14)
plt.yticks([0, 0.2, 0.4, 0.6, 0.8, 1.0], fontsize=14)
plt.ylim(-0.05, 1.05)
ax.set_xlabel("$ 1 - P$", fontsize=14)
ax.set_ylabel("Fraction in car trips", fontsize=14)

ax.plot(direct_failed_road_frac4Nanjing, car2car_samePath_rel, alpha=1, marker='o', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#66c2a5', linewidth=1.5, label='No change')
ax.plot(direct_failed_road_frac4Nanjing, car2car_pathChange_rel, alpha=1, marker='s', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#fc8d62', linewidth=1.5, label='Route switching')
ax.plot(direct_failed_road_frac4Nanjing, car2pt_rel, alpha=1, marker='D', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#8da0cb', linewidth=1.5, label='Mode shift')
ax.plot(direct_failed_road_frac4Nanjing, car2interruption_rel, alpha=1, marker='+', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#e78ac3', linewidth=1.5, label='Failed change')

lines, labels = [], []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.35,0.5), frameon=False, fontsize=14)

# plt.savefig(r"Your output file folder\car trips stream.relative.pdf", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')

""" 4. fig: pt trips """
########## preparation #########
# relative fraction: 
pt2pt_samePath_rel = [tripsTransResultsforA2B['Pt to pt no path change'] / (tripsTransResultsforA2B['Pt to pt no path change'] + tripsTransResultsforA2B['Pt to pt with path change'] + tripsTransResultsforA2B['Pt to car'] + tripsTransResultsforA2B['Pt to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]
pt2pt_pathChange_rel = [tripsTransResultsforA2B['Pt to pt with path change'] / (tripsTransResultsforA2B['Pt to pt no path change'] + tripsTransResultsforA2B['Pt to pt with path change'] + tripsTransResultsforA2B['Pt to car'] + tripsTransResultsforA2B['Pt to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]
pt2car_rel = [tripsTransResultsforA2B['Pt to car'] / (tripsTransResultsforA2B['Pt to pt no path change'] + tripsTransResultsforA2B['Pt to pt with path change'] + tripsTransResultsforA2B['Pt to car'] + tripsTransResultsforA2B['Pt to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]
pt2interruption_rel = [tripsTransResultsforA2B['Pt to interuption'] / (tripsTransResultsforA2B['Pt to pt no path change'] + tripsTransResultsforA2B['Pt to pt with path change'] + tripsTransResultsforA2B['Pt to car'] + tripsTransResultsforA2B['Pt to interuption']) for tripsTransResultsforA2B in tripsTransAllResults]

fig = plt.figure()
ax = fig.add_subplot(111)
plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in') 
ax.tick_params(which ='minor', direction='in')
plt.xticks(fontsize=14)
plt.yticks([0, 0.2, 0.4, 0.6, 0.8, 1.0], fontsize=14)
plt.ylim(-0.05, 1.05)
ax.set_xlabel("$ 1 - P$", fontsize=14)
ax.set_ylabel("Fraction in pt trips", fontsize=14)

ax.plot(direct_failed_pt_frac4Nanjing, pt2pt_samePath_rel, alpha=1, marker='o', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#66c2a5', linewidth=1.5, label='No adaptation')
ax.plot(direct_failed_pt_frac4Nanjing, pt2pt_pathChange_rel, alpha=1, marker='s', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#fc8d62', linewidth=1.5, label='Path adaptation')
ax.plot(direct_failed_pt_frac4Nanjing, pt2car_rel, alpha=1, marker='D', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#8da0cb', linewidth=1.5, label='Mode adaptation')
ax.plot(direct_failed_pt_frac4Nanjing, pt2interruption_rel, alpha=1, marker='+', fillstyle="none" , markersize=10, markeredgewidth=1.5, color='#e78ac3', linewidth=1.5, label='Failed adaptation')

lines, labels = [], []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.35,0.5), frameon=False, fontsize=14)

# plt.savefig(r"Your output file folder\pt trips stream.relative.pdf", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')