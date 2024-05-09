import geopandas as gpd
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import numpy as np
from osgeo import gdal
import struct
import statsmodels.api as sm

""" 1. function: count the number of car (or pt) links in each grid """
def getCarLinkNum(each_group):
    return len(each_group[each_group['my_mode'] == 'road'])

def getPtLinkNum(each_group):
    return len(each_group[each_group['my_mode'] == 'pt'])

def count_link(network_gdf, grids_gdf):
    baseline_sjoin_gdf = gpd.sjoin(left_df=network_gdf, right_df=grids_gdf,how="inner", predicate="intersects", rsuffix="right")  # we take the how="inner". If the grid has no link, we don't consider it. Vice versa.
    groups = baseline_sjoin_gdf.groupby("gridId")
    # the number of car link in each grid
    gridId2CarLinkNum = groups.apply(getCarLinkNum)  # count the number of car link for each grid group.  NOTE: "apply" the function to each group! This will return a series (index is gridId).
    gridId2PtLinkNum = groups.apply(getPtLinkNum)
    return gridId2CarLinkNum, gridId2PtLinkNum  # NOTE: return a series

""" 2. calculate the number of car link and pt link in each grid """
# threshold_list_nanjing = [0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
# threshold_list_hamburg = [0.0, 0.3, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
# threshold_list_la = [0.0, 0.003, 0.007, 0.01, 0.02, 0.07, 0.2, 0.3, 0.4, 0.6, 0.8, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]

print(">>>>>>>>>>> For Nanjing <<<<<<<<")
for threshold in threshold_list_nanjing:
    print(".....Threshold = ", str(threshold), ".....")
    # nanjing # NOTE: we need road link from the "xx_net_nanjing"; we need pt link from the "xx_pt_net_nanjing"
    flooded_net_nanjing = gpd.read_file(r"Your folder path\nanjing-network-indirect failure-threshold" + str(threshold) + "m.shp")
    flooded_pt_net_nanjing = gpd.read_file(r"Your folder path\nanjing-pt network-operational failure-threshold" + str(threshold) + "m.shp")
    # deal with the input network
    flooded_road_net_nanjing = flooded_net_nanjing[flooded_net_nanjing['modes'] != '[pt]'] 
    flooded_road_net_nanjing['my_mode'] = "road"
    flooded_pt_net_nanjing['my_mode'] = 'pt'
    flooded_net_nanjing = pd.concat([flooded_road_net_nanjing, flooded_pt_net_nanjing])
    flooded_net_nanjing.to_crs(epsg=4326, inplace=True)
    # count link num for each grid
    flooded_car_link_num_nanjing, flooded_pt_link_num_nanjing = count_link(network_gdf=flooded_net_nanjing, grids_gdf=grids_gdf_nanjing)
    flooded_car_link_num_nanjing.to_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold" + str(threshold) + ".csv")
    flooded_pt_link_num_nanjing.to_csv(r"Your folder path" + str(threshold) + ".csv")

print(">>>>>>>>>>> For Hamburg <<<<<<<<")
for threshold in threshold_list_hamburg:
    print(".....Threshold = ", str(threshold), ".....")
    # hamburg
    flooded_net_hamburg = gpd.read_file("Your folder path\\hamburg-network-pt failure-threshold" + str(threshold) + "m.shp")
    # deal with the input network
    my_modes_hamburg = ['pt' if mode == '[pt]' else 'road'  for mode in flooded_net_hamburg['modes']]
    flooded_net_hamburg['my_mode'] = my_modes_hamburg
    flooded_net_hamburg.to_crs(epsg=4326, inplace=True)
    # count link num for each grid
    flooded_car_link_num_hamburg, flooded_pt_link_num_hamburg = count_link(network_gdf=flooded_net_hamburg, grids_gdf=grids_gdf_hamburg)
    flooded_car_link_num_hamburg.to_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold" + str(threshold) + ".csv")
    flooded_pt_link_num_hamburg.to_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold" + str(threshold) + ".csv")

print(">>>>>>>>>>> For LA <<<<<<<<")
for threshold in threshold_list_la:
    print(".....Threshold = ", str(threshold), ".....")
    # LA
    flooded_net_LA = gpd.read_file("Your folder path\\my2-50th-los angeles-network-pt failure-threshold" + str(threshold)  + "m.shp")
    # deal with the input network
    my_modes_LA = ['pt' if mode == '[pt]' else 'road'  for mode in flooded_net_LA['modes']]
    flooded_net_LA['my_mode'] = my_modes_LA
    flooded_net_LA.to_crs(epsg=4326, inplace=True)
    # count link num for each grid
    flooded_car_link_num_la, flooded_pt_link_num_la = count_link(network_gdf=flooded_net_LA, grids_gdf=grids_gdf_LA)
    flooded_car_link_num_la.to_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold" + str(threshold) + ".csv")
    flooded_pt_link_num_la.to_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold" + str(threshold) + ".csv")



""" 3. import density data """
# for nanjing
# threshold
threshold_list_nanjing = [0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
print("for nanjing")
print("threshold: ", threshold_list_nanjing)
# car link
car_link_num_baseline_nanjing = pd.read_csv(r"Your folder path\baseline_car_link_num_in_each_grid_nanjing.csv")
car_link_num_floodTh0_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold0.0.csv")
car_link_num_floodTh1_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold1.0.csv")
car_link_num_floodTh2_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold2.0.csv")
car_link_num_floodTh3_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold3.0.csv")
car_link_num_floodTh4_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold4.0.csv")
car_link_num_floodTh5_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold5.0.csv")
car_link_num_floodTh6_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold6.0.csv")
car_link_num_floodTh7_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold7.0.csv")
car_link_num_floodTh8_nanjing = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_nanjing_threshold8.0.csv")
# pt link
pt_link_num_baseline_nanjing = pd.read_csv(r"Your folder path\baseline_pt_link_num_in_each_grid_nanjing.csv")
pt_link_num_floodTh0_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold0.0.csv")
pt_link_num_floodTh1_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold1.0.csv")
pt_link_num_floodTh2_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold2.0.csv")
pt_link_num_floodTh3_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold3.0.csv")
pt_link_num_floodTh4_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold4.0.csv")
pt_link_num_floodTh5_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold5.0.csv")
pt_link_num_floodTh6_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold6.0.csv")
pt_link_num_floodTh7_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold7.0.csv")
pt_link_num_floodTh8_nanjing = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_nanjing_threshold8.0.csv")

car_link_mean_nanjing = []
for i in [car_link_num_floodTh8_nanjing, car_link_num_floodTh7_nanjing, car_link_num_floodTh6_nanjing, car_link_num_floodTh5_nanjing, car_link_num_floodTh4_nanjing, \
          car_link_num_floodTh3_nanjing, car_link_num_floodTh2_nanjing, car_link_num_floodTh1_nanjing, car_link_num_floodTh0_nanjing]:
    car_link_mean_nanjing.append((i['link num'].mean()) / car_link_num_baseline_nanjing['link num'].mean())
pt_link_std_nanjing_inundated_grids = []
for i in [pt_link_num_floodTh8_nanjing, pt_link_num_floodTh7_nanjing, pt_link_num_floodTh6_nanjing, pt_link_num_floodTh5_nanjing, pt_link_num_floodTh4_nanjing, \
          pt_link_num_floodTh3_nanjing, pt_link_num_floodTh2_nanjing, pt_link_num_floodTh1_nanjing, pt_link_num_floodTh0_nanjing]:
    pt_link_mean_nanjing.append((i['link num'].mean()) / pt_link_num_baseline_nanjing['link num'].mean())


# for hamburg
# threshold
print("for hamburg")
threshold_list_hamburg = [0.0, 0.3, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
print("threshold: ", threshold_list_hamburg)

# car link
car_link_num_baseline_hamburg = pd.read_csv(r"Your folder path\baseline_car_link_num_in_each_grid_hamburg.csv")
car_link_num_floodTh0_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold0.0.csv")
car_link_num_floodTh1_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold1.0.csv")
car_link_num_floodTh2_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold2.0.csv")
car_link_num_floodTh3_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold3.0.csv")
car_link_num_floodTh4_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold4.0.csv")
car_link_num_floodTh5_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold5.0.csv")
car_link_num_floodTh6_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold6.0.csv")
car_link_num_floodTh7_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold7.0.csv")
car_link_num_floodTh8_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold8.0.csv")
car_link_num_floodTh0_3_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold0.3.csv")
car_link_num_floodTh1_2_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold1.2.csv")
car_link_num_floodTh1_4_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold1.4.csv")
car_link_num_floodTh1_6_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold1.6.csv")
car_link_num_floodTh1_8_hamburg = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_hamburg_threshold1.8.csv")

# pt link
pt_link_num_baseline_hamburg = pd.read_csv(r"Your folder path\baseline_pt_link_num_in_each_grid_hamburg.csv")
pt_link_num_floodTh0_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold0.0.csv")
pt_link_num_floodTh1_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold1.0.csv")
pt_link_num_floodTh2_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold2.0.csv")
pt_link_num_floodTh3_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold3.0.csv")
pt_link_num_floodTh4_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold4.0.csv")
pt_link_num_floodTh5_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold5.0.csv")
pt_link_num_floodTh6_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold6.0.csv")
pt_link_num_floodTh7_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold7.0.csv")
pt_link_num_floodTh8_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold8.0.csv")
pt_link_num_floodTh0_3_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold0.3.csv")
pt_link_num_floodTh1_2_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold1.2.csv")
pt_link_num_floodTh1_4_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold1.4.csv")
pt_link_num_floodTh1_6_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold1.6.csv")
pt_link_num_floodTh1_8_hamburg = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_hamburg_threshold1.8.csv")

car_link_mean_hamburg = []
for i in [car_link_num_floodTh8_hamburg, car_link_num_floodTh7_hamburg, car_link_num_floodTh6_hamburg, car_link_num_floodTh5_hamburg, car_link_num_floodTh4_hamburg, \
          car_link_num_floodTh3_hamburg, car_link_num_floodTh2_hamburg, car_link_num_floodTh1_8_hamburg, car_link_num_floodTh1_6_hamburg, car_link_num_floodTh1_4_hamburg, 
          car_link_num_floodTh1_2_hamburg, car_link_num_floodTh1_hamburg, car_link_num_floodTh0_3_hamburg, car_link_num_floodTh0_hamburg]:
    car_link_mean_hamburg.append((i['link num'].mean()) / car_link_num_baseline_hamburg['link num'].mean())

pt_link_mean_hamburg = []
for i in [pt_link_num_floodTh8_hamburg, pt_link_num_floodTh7_hamburg, pt_link_num_floodTh6_hamburg, pt_link_num_floodTh5_hamburg, pt_link_num_floodTh4_hamburg, \
          pt_link_num_floodTh3_hamburg, pt_link_num_floodTh2_hamburg, pt_link_num_floodTh1_8_hamburg, pt_link_num_floodTh1_6_hamburg, pt_link_num_floodTh1_4_hamburg, 
          pt_link_num_floodTh1_2_hamburg, pt_link_num_floodTh1_hamburg, pt_link_num_floodTh0_3_hamburg, pt_link_num_floodTh0_hamburg]:
    pt_link_mean_hamburg.append((i['link num'].mean()) / pt_link_num_baseline_hamburg['link num'].mean())

# for la
# threshold
print("for Los Angeles")
threshold_list_la = [0.0, 0.003, 0.007, 0.01, 0.02, 0.07, 0.2, 0.3, 0.4, 0.6, 0.8, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
print("threshold: ", threshold_list_la)

# car link
car_link_num_baseline_la = pd.read_csv(r"Your folder path\baseline_car_link_num_in_each_grid_la.csv")
car_link_num_floodTh0_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.0.csv")
car_link_num_floodTh1_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold1.0.csv")
car_link_num_floodTh2_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold2.0.csv")
car_link_num_floodTh3_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold3.0.csv")
car_link_num_floodTh4_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold4.0.csv")
car_link_num_floodTh5_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold5.0.csv")
car_link_num_floodTh6_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold6.0.csv")
car_link_num_floodTh7_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold7.0.csv")
car_link_num_floodTh8_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold8.0.csv")

car_link_num_floodTh0_2_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.2.csv")
car_link_num_floodTh0_3_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.3.csv")
car_link_num_floodTh0_4_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.4.csv")
car_link_num_floodTh0_6_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.6.csv")
car_link_num_floodTh0_8_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.8.csv")

car_link_num_floodTh0_003_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.003.csv")
car_link_num_floodTh0_007_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.007.csv")
car_link_num_floodTh0_01_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.01.csv")
car_link_num_floodTh0_02_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.02.csv")
car_link_num_floodTh0_07_la = pd.read_csv(r"Your folder path\flood_car_link_num_in_each_grid_la_threshold0.07.csv")

# pt link
pt_link_num_baseline_la = pd.read_csv(r"Your folder path\baseline_pt_link_num_in_each_grid_la.csv")
pt_link_num_floodTh0_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.0.csv")
pt_link_num_floodTh1_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold1.0.csv")
pt_link_num_floodTh2_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold2.0.csv")
pt_link_num_floodTh3_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold3.0.csv")
pt_link_num_floodTh4_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold4.0.csv")
pt_link_num_floodTh5_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold5.0.csv")
pt_link_num_floodTh6_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold6.0.csv")
pt_link_num_floodTh7_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold7.0.csv")
pt_link_num_floodTh8_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold8.0.csv")

pt_link_num_floodTh0_2_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.2.csv")
pt_link_num_floodTh0_3_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.3.csv")
pt_link_num_floodTh0_4_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.4.csv")
pt_link_num_floodTh0_6_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.6.csv")
pt_link_num_floodTh0_8_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.8.csv")

pt_link_num_floodTh0_003_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.003.csv")
pt_link_num_floodTh0_007_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.007.csv")
pt_link_num_floodTh0_01_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.01.csv")
pt_link_num_floodTh0_02_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.02.csv")
pt_link_num_floodTh0_07_la = pd.read_csv(r"Your folder path\flood_pt_link_num_in_each_grid_la_threshold0.07.csv")


car_link_mean_la = []
for i in [car_link_num_floodTh8_la, car_link_num_floodTh7_la, car_link_num_floodTh6_la, car_link_num_floodTh5_la, car_link_num_floodTh4_la, \
          car_link_num_floodTh3_la, car_link_num_floodTh2_la, car_link_num_floodTh1_la, car_link_num_floodTh0_8_la, car_link_num_floodTh0_6_la,
          car_link_num_floodTh0_4_la, car_link_num_floodTh0_3_la, car_link_num_floodTh0_2_la, car_link_num_floodTh0_07_la, car_link_num_floodTh0_02_la, 
          car_link_num_floodTh0_01_la, car_link_num_floodTh0_007_la, car_link_num_floodTh0_003_la, car_link_num_floodTh0_la]:
    car_link_mean_la.append((i['link num'].mean()) / car_link_num_baseline_la['link num'].mean())

pt_link_mean_la = []
pt_link_std_la_inundated_grids = []
for i in [pt_link_num_floodTh8_la, pt_link_num_floodTh7_la, pt_link_num_floodTh6_la, pt_link_num_floodTh5_la, pt_link_num_floodTh4_la, \
          pt_link_num_floodTh3_la, pt_link_num_floodTh2_la, pt_link_num_floodTh1_la, pt_link_num_floodTh0_8_la, pt_link_num_floodTh0_6_la,
          pt_link_num_floodTh0_4_la, pt_link_num_floodTh0_3_la, pt_link_num_floodTh0_2_la, pt_link_num_floodTh0_07_la, pt_link_num_floodTh0_02_la, 
          pt_link_num_floodTh0_01_la, pt_link_num_floodTh0_007_la, pt_link_num_floodTh0_003_la, pt_link_num_floodTh0_la]:
    pt_link_mean_la.append((i['link num'].mean()) / pt_link_num_baseline_la['link num'].mean())


""" 4. Behavioral transition results, preparing plotting """
behavior_result_nanjing = pd.read_csv(r"Your folder path\result for nanjing.csv")
first_line = behavior_result_nanjing.iloc[0]
behavior_result_nanjing.drop(index=[0, 1, 2, 3], inplace=True)
# behavior_result_nanjing.drop(columns=['car2car_samePath_rel_2', 'car2car_pathChange_rel_2', 'car2pt_rel_2','pt2pt_samePath_rel_2', 
#                                       'pt2pt_pathChange_rel_2', 'pt2car_rel_2', 'car2pt_rel_deta', 'pt2car_rel_deta', 'car2car_pathChange_rel_deta', 
#                                       'pt2pt_pathChange_rel_deta', 'car2car_samePath_rel_deta', 'pt2pt_samePath_rel_deta'], inplace=True)
behavior_result_nanjing['direct_failed_road_frac'] = behavior_result_nanjing['direct_failed_road_frac4Nanjing']
behavior_result_nanjing['direct_failed_pt_frac'] = behavior_result_nanjing['direct_failed_pt_frac4Nanjing']
behavior_result_nanjing['City'] = 'Nanjing'
behavior_result_nanjing['car_link_mean'] = car_link_mean_nanjing
behavior_result_nanjing['pt_link_mean'] = pt_link_mean_nanjing
behavior_result_nanjing['car2pt_rel_deta'] = behavior_result_nanjing['car2pt_rel'] - first_line['car2pt_rel']
behavior_result_nanjing['pt2car_rel_deta'] = behavior_result_nanjing['pt2car_rel'] - first_line['pt2car_rel']

behavior_result_hamburg = pd.read_csv(r"Your folder path\result for hamburg.csv")
first_line = behavior_result_hamburg.iloc[0]
behavior_result_hamburg['direct_failed_road_frac'] = behavior_result_hamburg['direct_failed_road_frac4Hamburg']
behavior_result_hamburg['direct_failed_pt_frac'] = behavior_result_hamburg['direct_failed_pt_frac4Hamburg']
behavior_result_hamburg['City'] = 'Hamburg'
behavior_result_hamburg['car_link_mean'] = car_link_mean_hamburg
behavior_result_hamburg['pt_link_mean'] = pt_link_mean_hamburg
behavior_result_hamburg['car2pt_rel_deta'] = behavior_result_hamburg['car2pt_rel'] - first_line['car2pt_rel']
behavior_result_hamburg['pt2car_rel_deta'] = behavior_result_hamburg['pt2car_rel'] - first_line['pt2car_rel']

behavior_result_la = pd.read_csv(r"Your folder path\result for la.csv")
first_line = behavior_result_la.iloc[0]
behavior_result_la['direct_failed_road_frac'] = behavior_result_la['direct_failed_road_frac4LA']
behavior_result_la['direct_failed_pt_frac'] = behavior_result_la['direct_failed_pt_frac4LA']
behavior_result_la['City'] = 'LA'
behavior_result_la['car_link_mean'] = car_link_mean_la
behavior_result_la['pt_link_mean'] = pt_link_mean_la
behavior_result_la['car2pt_rel_deta'] = behavior_result_la['car2pt_rel'] - first_line['car2pt_rel']
behavior_result_la['pt2car_rel_deta'] = behavior_result_la['pt2car_rel'] - first_line['pt2car_rel']

behavior_result_nanjing.drop(columns=['Unnamed: 0', 'direct_failed_road_frac4Nanjing', 'direct_failed_pt_frac4Nanjing'], inplace=True)
behavior_result_hamburg.drop(columns=['Unnamed: 0', 'direct_failed_road_frac4Hamburg', 'direct_failed_pt_frac4Hamburg'], inplace=True)
behavior_result_la.drop(columns=['Unnamed: 0', 'direct_failed_road_frac4LA', 'direct_failed_pt_frac4LA'], inplace=True)

behavior_results = pd.concat([behavior_result_nanjing, behavior_result_hamburg, behavior_result_la])
behavior_results.reset_index(inplace=True,drop=True)

""" 5. linear fitting """
""" 5.1 Nanjing: car ---> pt """
y = behavior_results[behavior_results['City'] == 'Nanjing']['car2pt_rel_deta']
# X = behavior_results[behavior_results['City'] == 'Nanjing']['car_link_mean'] - behavior_results[behavior_results['City'] == 'Nanjing']['pt_link_mean']# 预测值
X = behavior_results[behavior_results['City'] == 'Nanjing']['pt_link_mean'] - behavior_results[behavior_results['City'] == 'Nanjing']['car_link_mean']
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
nanjing_car2pt_pre = res.predict(X)

""" 5.2 Nanjing: pt ---> car """
y = behavior_results[behavior_results['City'] == 'Nanjing']['pt2car_rel_deta']
X = behavior_results[behavior_results['City'] == 'Nanjing']['car_link_mean'] - behavior_results[behavior_results['City'] == 'Nanjing']['pt_link_mean']
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
nanjing_pt2car_pre = res.predict(X)

""" 5.3 Hamburg (T>=1.8m): car ---> pt """
y = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['car2pt_rel_deta']
# X = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['car_link_mean'] - behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['pt_link_mean']
X = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['pt_link_mean'] - behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['car_link_mean']
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
hamburg1_car2pt_pre = res.predict(X)

""" 5.4 Hamburg (T>=1.8m): pt ---> car """
y = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['pt2car_rel_deta']
X = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['car_link_mean'] - behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] >= 1.8) ]['pt_link_mean']
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
hamburg1_pt2car_pre = res.predict(X)

""" 5.5 Hamburg (T<1.8m): car ---> pt """
y = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['car2pt_rel_deta']
# X = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['car_link_mean'] - behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['pt_link_mean']
X = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['pt_link_mean'] - behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['car_link_mean']
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
hamburg2_car2pt_pre = res.predict(X)

""" 5.6 Hamburg (T<1.8m): pt ---> car """
y = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['pt2car_rel_deta']
X = behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['car_link_mean'] - behavior_results[(behavior_results['City'] == 'Hamburg') & (behavior_results['threshold'] < 1.8) ]['pt_link_mean']
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
hamburg2_pt2car_pre = res.predict(X)

""" 5.7 LA : car ---> pt """
y = behavior_results[(behavior_results['City'] == 'LA')]['car2pt_rel_deta']
# X = behavior_results[(behavior_results['City'] == 'LA')]['car_link_mean'] - behavior_results[(behavior_results['City'] == 'LA')]['pt_link_mean']
X = behavior_results[(behavior_results['City'] == 'LA')]['pt_link_mean'] - behavior_results[(behavior_results['City'] == 'LA')]['car_link_mean']
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
la_car2pt_pre = res.predict(X)

""" 5.8 LA : pt ---> car """
y = behavior_results[(behavior_results['City'] == 'LA')]['pt2car_rel_deta']
X = behavior_results[(behavior_results['City'] == 'LA')]['car_link_mean'] - behavior_results[(behavior_results['City'] == 'LA')]['pt_link_mean'] 
X = sm.add_constant(X)
mod = sm.OLS(y, X)
res = mod.fit()
print(res.summary())
# predict values
la_pt2car_pre = res.predict(X)

""" 6. plot figures """
""" 6.1 for car to pt """
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)

# for nanjing
behavior_result_nanjing['density_gap'] = behavior_result_nanjing['pt_link_mean'] - behavior_result_nanjing['car_link_mean']
sns.regplot(x='density_gap', \
    y='car2pt_rel_deta', 
    data=behavior_result_nanjing,
    ci=95,
    ax=ax,
    label='Nanjing')
# for hamburg (T>=1.8)
behavior_result_hamburg1 = behavior_result_hamburg[behavior_result_hamburg['threshold'] >= 1.8]
behavior_result_hamburg1['density_gap'] = behavior_result_hamburg1['pt_link_mean'] - behavior_result_hamburg1['car_link_mean']
sns.regplot(x='density_gap', \
    y='car2pt_rel_deta', 
    data=behavior_result_hamburg1,
    ci=95,
    ax=ax,
    label='Hamburg (T≥1.8m)')
# for hamburg (T <1.8)
behavior_result_hamburg2 = behavior_result_hamburg[behavior_result_hamburg['threshold'] < 1.8]
behavior_result_hamburg2['density_gap'] = behavior_result_hamburg2['pt_link_mean'] - behavior_result_hamburg2['car_link_mean']
sns.regplot(x='density_gap', \
    y='car2pt_rel_deta', 
    data=behavior_result_hamburg2,
    ci=95,
    ax=ax,
    label='Hamburg (T<1.8m)')

# for la
behavior_result_la['density_gap'] = behavior_result_la['pt_link_mean'] - behavior_result_la['car_link_mean']
sns.regplot(x='density_gap', \
    y='car2pt_rel_deta', 
    data=behavior_result_la,
    ci=95,
    ax=ax,
    label='LA')
plt.axhline(0, color='black', linestyle='--', linewidth=1)
plt.axvline(0, color='black', linestyle='--', linewidth=1)

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.4,0.5), frameon=False, fontsize=14)
ax.set_ylabel("$f_{car}$", fontsize=14)
ax.set_xlabel("$S_{pt} - S_{car}$", fontsize=14)
plt.yticks(fontsize=14)
plt.xticks(fontsize=14)

plt.savefig(r"\car to pt versus density gap.pdf", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')

""" 6.2 for pt to car """
behavior_result_nanjing['density_gap'] = behavior_result_nanjing['car_link_mean'] - behavior_result_nanjing['pt_link_mean']
behavior_result_hamburg1['density_gap'] = behavior_result_hamburg1['car_link_mean'] - behavior_result_hamburg1['pt_link_mean']
behavior_result_hamburg2['density_gap'] = behavior_result_hamburg2['car_link_mean'] - behavior_result_hamburg2['pt_link_mean']
behavior_result_la['density_gap'] = behavior_result_la['car_link_mean'] - behavior_result_la['pt_link_mean']

from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)

# for nanjing
sns.regplot(x='density_gap', \
    y='pt2car_rel_deta', 
    data=behavior_result_nanjing,
    ci=95,
    ax=ax,
    label='Nanjing')

# for hamburg (T>=1.8)
sns.regplot(x='density_gap', \
    y='pt2car_rel_deta', 
    data=behavior_result_hamburg1,
    ci=95,
    ax=ax,
    label='Hamburg (T≥1.8m)')

# for hamburg (T < 1.8)
sns.regplot(x='density_gap', \
    y='pt2car_rel_deta', 
    data=behavior_result_hamburg2,
    ci=95,
    ax=ax,
    label='Hamburg (T<1.8m)')

# for la
sns.regplot(x='density_gap', \
    y='pt2car_rel_deta', 
    data=behavior_result_la,
    ci=95,
    ax=ax,
    label='LA')

plt.axhline(0, color='black', linestyle='--', linewidth=1)
plt.axvline(0, color='black', linestyle='--', linewidth=1)

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.4,0.5), frameon=False, fontsize=14)
ax.set_ylabel("$f_{pt}$", fontsize=14)
ax.set_xlabel("$S_{car} - S_{pt}$", fontsize=14)
plt.yticks(fontsize=14)
plt.xticks(fontsize=14)
plt.savefig(r"Your output folder path\pt to car versus density gap.pdf", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')