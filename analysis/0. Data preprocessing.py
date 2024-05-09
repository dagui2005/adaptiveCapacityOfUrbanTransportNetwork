from pyproj import Transformer
import pandas as pd
import os
import numpy as np

# for nanjing scenario (It can be the baseline scenario or a flooding scenario).
folder = "Your output folder path\\"
input_projection = 'EPSG:32650'  # NOTE: projection coordination system. Nanjing, China (epsg:32650); hamburg (epsg:25832); los angeles (epsg:3310) 
input_file = os.path.join(folder,"output_trips.csv.gz")    # the output file from simulation
output_file = os.path.join(folder,"output_trips_wgs84.csv.gz")
transformer = Transformer.from_crs(input_projection, 'EPSG:4326')
trips = pd.read_csv(input_file, sep=';', compression="gzip")
############### only save person whose daily trips only use car or public transport modes    #################
personIds = pd.read_csv("The path of the file that contains the agent id who only use car or public tranpsort modes in their daily trips")
useful_personIds = set(personIds['personId'])
trips = trips[trips['person'].isin(useful_personIds)]
latList,lonList = [],[]
for startX,startY in zip(trips['start_x'],trips['start_y']):
    lat, lon = transformer.transform(startX,startY)  # Note: x corresponds to lat, y corresponds to lon
    latList.append(lat)
    lonList.append(lon)
trips['start_lat'] = latList
trips['start_lon'] = lonList
latList,lonList = [],[]
for endX,endY in zip(trips['end_x'],trips['end_y']):
    lat, lon = transformer.transform(endX,endY)
    latList.append(lat)
    lonList.append(lon)
trips['end_lat'] = latList
trips['end_lon'] = lonList
# Function: Time conversion. Converts 1:00:00 to 60 min
def converTime(time0):
    hours = int(time0.split(":")[0])
    minutes = int(time0.split(":")[1])
    seconds = int(time0.split(":")[2])
    # return hours * 60 + minutes + seconds / 60
    return hours * 60 + minutes
time = [converTime(i) for i in trips['trav_time']]
trips['travel_time(min)'] = time
################## delte trips with specific leg mode chain that we don't want ###################
trip_cate = list(trips.modes.drop_duplicates())
trip_count = [len(trips[trips["modes"]==trip_cate])  for trip_cate in list(trips.modes.drop_duplicates())]
trip_fraction_count = [len(trips[trips["modes"]==trip_cate]) / len(trips)  for trip_cate in list(trips.modes.drop_duplicates())]
removed_modes4nanjing = ["walk"]  # for nanjing. Firstly walking is not a mode of transport we want to study
# removed_modes4hamburg = ["walk", "bike", "walk-ride-walk", 'walk-commercial_Lfw-walk', 'walk-commercial_Trans-walk', 'walk-commercial_Lkw-g-walk', 'walk-commercial_Lkw-k-walk', 'walk-commercial_Lkw-m-walk']
# removed_modes4LA = ['ride', 'walk', 'ride_school_bus', 'ride_taxi', 'bike', 'freight']
trips = trips[~trips.modes.isin(removed_modes4nanjing)]
trips.to_csv(output_file, sep=';', compression="gzip")

input_file = os.path.join(folder,"output_trips_wgs84.csv")
output_file = os.path.join(folder,"output_trips_wgs84_cleaned.csv")
trips = pd.read_csv(input_file, sep=";")
##########  Delete trips with 0 travel time or travel distances  #############
dis_zero_num = len(trips[trips["traveled_distance"] == 0])
trips.drop(index=trips[trips["traveled_distance"] == 0].index, inplace=True)
time_zero_num = len(trips[trips["travel_time(min)"] == 0])
trips.drop(index=trips[trips["travel_time(min)"] == 0].index, inplace=True)
# ######### Remove invalid samples-travel distance less than Euclidean distance  #############
p = (trips.traveled_distance < trips.euclidean_distance)
travel_dis_eucl_dis_num = np.sum(p != 0)
trips = trips[trips.traveled_distance > trips.euclidean_distance]
######### Deletion of invalid samples - travel time over 300min  #############
travel_time_induced_num.append(len(trips[trips['travel_time(min)'] > 300]))
trips.drop(index = trips[trips['travel_time(min)'] > 300].index, inplace=True) 
######### Delete invalid information and add valid information  #############
trips.drop(columns=['Unnamed: 0', 'trip_number', 'dep_time', \
        'trav_time', 'main_mode', 'longest_distance_mode', 'start_activity_type', \
        'end_activity_type', 'start_facility_id', 'start_link', 'end_facility_id', 'end_link', \
                'first_pt_boarding_stop', 'last_pt_egress_stop'], inplace=True)
trips['my_modes'] = ["car" if mode=="car" else "pt" for mode in trips.modes]  # NOTE: Modes name is different for different cities.
trips["pt_times"] = [0 if mode=="car" else ((len(mode.split('-pt-'))) - 1 ) for mode in trips.modes]    # Public transportation use count (0 for cars)  NOTE: Modes name is different for different cities.
trips.to_csv(output_file, sep=';')