import seaborn as sns
import pandas as pd
import geopandas as gpd
import numpy as np
import matplotlib.pyplot as plt
from matplotlib import colors, colorbar, cm, ticker
from shapely import LineString
import ast

""" 0. import trips data """
# 0.1 trips data
# Nanjing, China (epsg:32650); hamburg (epsg:25832); los angeles (epsg:3310)
crs = 32650
path = r"Your folder path for a scenario"+ "\\output_trips_wgs84_cleaned.csv"
trips_nj = pd.read_csv(path, sep=";")

# 0.2 legs data
# for flooding
path = r"Your folder path for a scenario" + "\\output_legs.csv.gz"
legs_nj = pd.read_csv(path, sep=";", compression="gzip", dtype={"transit_line": str})[['trip_id', 'mode', 'transit_line']]

# 0.3 public transit lines data
pt_base_nj = pd.read_csv(r"Your folder path\nanjingBaseTransitSchedule.csv", dtype={"ptLineId": str})[['ptLineId', 'TransportMode']]

""" 1. get the modes (car, bus, train) used in each trip """
# 0.4 preprocessing
# add each leg for each trip. NOTE: each row represents a leg now.
trips_nj_new = pd.merge(left=trips_nj, right=legs_nj, on="trip_id", how="left")
# add each mode for each (pt) leg.
trips_nj_new = pd.merge(left=trips_nj_new, right=pt_base_nj, left_on="transit_line", right_on="ptLineId", how="left")
# replace "pt" with "bus" or "subway" for each leg.
mask = ~trips_nj_new['TransportMode'].isnull()   # NOTE: "mask" extract the legs of pt trip.
trips_nj_new.loc[mask, 'mode'] = trips_nj_new.loc[mask, 'TransportMode']
# delete unecessary columns
trips_nj_new.drop(columns=['Unnamed: 0', 'start_x', 'start_y', 'end_x', 'end_y', 'ptLineId', 'TransportMode'], inplace=True)
trips_nj_new.head()

# NOTE: each row represent a leg.

# group and merge legs to trips. NOTE: each row represent a trip now.
# combine "mode" of each leg, combine "transit_line" of each leg, others stay the same.
trips_nj_new = trips_nj_new.groupby("trip_id").agg({"person":"first", 
                                              "traveled_distance":"first",
                                              "euclidean_distance":"first",
                                              "modes":"first",
                                              "travel_time(min)": "first",
                                              "my_modes":"first",
                                              "pt_times":"first",
                                              "start_lat": "first",
                                              "start_lon": "first",
                                              "end_lat": "first",
                                              "end_lon": "first",
                                              "mode": lambda x:list(x), 
                                              "transit_line": lambda x:list(x)}).reset_index()
trips_nj_new = trips_nj_new.rename(columns={"mode": "my_modes2"})
trips_nj_new.head()


# """ 2. Grid-based aggregation """
# """ 2.1 give the spatial attributes to the trips """
o_coords = gpd.points_from_xy(trips_nj_new['start_lon'], trips_nj_new['start_lat'], crs="epsg:4326")
d_coords = gpd.points_from_xy(trips_nj_new['end_lon'], trips_nj_new['end_lat'], crs="epsg:4326")

# NOTE: gpd.GeoDataFrame 是浅层复制，是引用！
trips_nj_o_gdf = gpd.GeoDataFrame(trips_nj_new.copy(True), geometry=o_coords)
trips_nj_d_gdf = gpd.GeoDataFrame(trips_nj_new.copy(True), geometry=d_coords)

""" 2.2 create the network """
xmin1, ymin1, xmax1, ymax1 = trips_nj_o_gdf.to_crs(crs=crs).total_bounds  # origin bounds 
xmin2, ymin2, xmax2, ymax2 = trips_nj_d_gdf.to_crs(crs=crs).total_bounds  # destination bounds
xmin = min(xmin1, xmin2)
ymin = min(ymin1, ymin2)
xmax = max(xmax1, xmax2)
ymax = max(ymax1, ymax2)
print("xmin = ", xmin, "ymin = ", ymin, "xmax = ", xmax, "ymax = ", ymax)
grid_size = 1000  # grid size
x_grid = np.arange(xmin, xmax, grid_size)
y_grid = np.arange(ymin, ymax, grid_size)
grid_cells = [(x, y) for x in x_grid for y in y_grid]

# rasterize the origin data and destination point.
trips_nj_o_gdf['o_grid_id'] = trips_nj_o_gdf.to_crs(crs=crs).apply(lambda row: (int((row.geometry.x - xmin) // grid_size), int((row.geometry.y - ymin) // grid_size)), axis=1)
trips_nj_d_gdf['d_grid_id'] = trips_nj_d_gdf.to_crs(crs=crs).apply(lambda row: (int((row.geometry.x - xmin) // grid_size), int((row.geometry.y - ymin) // grid_size)), axis=1)

# combine the origin and destination
trips_nj_gdf = pd.merge(left=trips_nj_o_gdf, 
                             right=trips_nj_d_gdf[['trip_id', "d_grid_id"]],
                             on="trip_id")

""" 3. calulate the relationship in each pair of OD """
# NOTE: new method to calculate the relationship. @Chunhong'2312
# Apply function. return the trip numebr, competition efficient (0 - 1) and competition result (0-1) for this group.
def get_comp_values(df):
    trips_only_car = 0
    trips_pt = 0

    trips_only_bus = 0
    trips_only_sub = 0
    trips_bus_sub = 0
    
    trips_others = 0

    for modes_list in df['my_modes2']:
        if "car" in modes_list:
            trips_only_car += 1
        elif "bus" in modes_list and "train" in modes_list:
            trips_bus_sub += 1
            trips_pt += 1
        elif "bus" in modes_list and "train" not in modes_list:
            trips_only_bus += 1
            trips_pt += 1
        elif "train" in modes_list and "bus" not in modes_list:
            trips_only_sub += 1
            trips_pt += 1
        else:
            trips_others += 1

    # the relationship between bus and subway. (competition result 1 + competition result 2 + complemention (undirected))
    if (trips_only_bus + trips_only_sub + trips_bus_sub) > 0: 
        bus_win = trips_only_bus / (trips_only_bus + trips_only_sub + trips_bus_sub)
        sub_win = trips_only_sub / (trips_only_bus + trips_only_sub + trips_bus_sub)
        bus_sub_compl = trips_bus_sub / (trips_only_bus + trips_only_sub + trips_bus_sub)
    else:
        bus_win, sub_win, bus_sub_compl = -1, -1, -1
    trip_count_bus_sub = (trips_only_bus + trips_only_sub + trips_bus_sub)

    # the relationship between car and pt.
    car_win = trips_only_car / (trips_only_car + trips_pt)
    pt_win = trips_pt / (trips_only_car + trips_pt)
    car_pt_compl = 0
    trip_count_car_pt = (trips_only_car + trips_pt)
    
    # travel distance # TODO: consider mean value firstly.
    travel_radius_mean = df['euclidean_distance'].mean()
    travel_distance_mean = df['traveled_distance'].mean()
    travel_time_mean = df['travel_time(min)'].mean()
    return [trip_count_bus_sub, bus_win, sub_win, bus_sub_compl], [trip_count_car_pt, car_win, pt_win, car_pt_compl], [travel_radius_mean, travel_distance_mean, travel_time_mean]


""" 4. plot figures for the relationship between bus and subway """
sns_df = pd.DataFrame()
# NOTE: tuple is transformed to string. So I use "ast.literal_eval()" to transform it.
sns_df['trip_count_bus_sub'] = results['result'].apply(lambda row: row[0][0])
sns_df['bus_win'] = results['result'].apply(lambda row: row[0][1])
sns_df['sub_win'] = results['result'].apply(lambda row: row[0][2])
sns_df['bus_sub_compl'] = results['result'].apply(lambda row: row[0][3])

sns_df['trip_count_car_pt'] = results['result'].apply(lambda row: row[1][0])
sns_df['car_win'] = results['result'].apply(lambda row: row[1][1])
sns_df['pt_win'] = results['result'].apply(lambda row: row[1][2])
sns_df['car_pt_compl'] = results['result'].apply(lambda row: row[1][3])

sns_df['travel_radius_mean'] = results['result'].apply(lambda row: row[2][0])    # unit: m
sns_df['travel_distance_mean'] = results['result'].apply(lambda row: row[2][1])   # unit: m
sns_df['travel_time_mean'] = results['result'].apply(lambda row: row[2][2])   # unit: min

# adjust the unit
sns_df['travel_radius_mean'] = sns_df['travel_radius_mean'] / 1000   # unit: km
sns_df['travel_distance_mean'] = sns_df['travel_distance_mean'] / 1000   # unit: km
sns_df['trip_frac_bus_sub'] = sns_df['trip_count_bus_sub'] / sns_df['trip_count_bus_sub'].sum()
sns_df['trip_frac_car_pt'] = sns_df['trip_count_car_pt'] / sns_df['trip_count_car_pt'].sum()


""" 4.1 line figure about the relationship between bus and subway"""
# plot line figure. x is 3rd attributes, y is the proportion contributed. 
third_attributes_list = ["travel_radius_mean", "travel_distance_mean", "travel_time_mean"]
third_attribute = third_attributes_list[0]
step = 1  # unit: km

# aggregate third attributes to bins with equal height.
bins = np.arange(start=sns_df[third_attribute].min(), stop=sns_df[third_attribute].max() + step, step=step)
group_index = pd.cut(sns_df[third_attribute], bins=bins, labels=False) # 0, 1, 2, ...
# third_attribute_ajusted = sns_df[third_attribute].min() + step / 2 + group_index * step   # adjusted thrid attribute values. use the medium value of this bin
third_attribute_ajusted = sns_df[third_attribute].min() + group_index * step   # adjusted thrid attribute values. use the left value of this bin.
sns_df['third_attribute_adjusted'] = third_attribute_ajusted

# plot line plot with CI
sns.set(font="Arial", font_scale=1.5, style="ticks")
ax = sns.lineplot(data=sns_df[sns_df['bus_sub_compl'] != -1], x="third_attribute_adjusted", y="bus_win", label="Bus prevalence", color="#e41a1c")
sns.lineplot(data=sns_df[sns_df['bus_sub_compl'] != -1], x="third_attribute_adjusted", y="sub_win", ax=ax, label="Subway prevalence", color="#377eb8")
sns.lineplot(data=sns_df[sns_df['bus_sub_compl'] != -1], x="third_attribute_adjusted", y="bus_sub_compl", ax=ax, label="Complementation", color="#4daf4a")
plt.legend(bbox_to_anchor=(1.8, 0.5), frameon=False)

# plot distribution of pt trips (only consider bus and subway now)
ax2 = ax.twinx()
sns_df2 = trips_nj_new.copy(True)
mask2 = sns_df2.apply(lambda row: True if ("bus" in row['my_modes2']) or ("train" in row['my_modes2']) else False, axis=1)
sns_df2['euclidean_distance_km'] = sns_df2['euclidean_distance'] / 1000
sns.histplot(data=sns_df2[mask2], x="euclidean_distance_km", binwidth=1, ax=ax2, stat="probability", fill=False, kde=True, color="gray", legend=False)

ax.set_xlabel("Travel radius (km)")
ax.set_ylabel("Relationship compostion")
ax2.set_ylabel("Trips proportion")
ax.set_xlim([0, 21])
ax.get_yaxis().set_minor_locator(ticker.AutoMinorLocator(2))
ax2.get_yaxis().set_minor_locator(ticker.AutoMinorLocator(2))

plt.savefig(r"Your output folder path\lineplot relationship for each OD pair.pdf", facecolor="white", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')


""" 5. plot figures for the competition result between car and pt """
""" 5.1 line figure """
# plot line figure. x is 3rd attributes, y is the proportion contributed. 
third_attributes_list = ["travel_radius_mean", "travel_distance_mean", "travel_time_mean"]
third_attribute = third_attributes_list[0]
step = 1  # unit: km

# aggregate third attributes to bins with equal height.
bins = np.arange(start=sns_df[third_attribute].min(), stop=sns_df[third_attribute].max() + step, step=step)
group_index = pd.cut(sns_df[third_attribute], bins=bins, labels=False) # 0, 1, 2, ...
# third_attribute_ajusted = sns_df[third_attribute].min() + step / 2 + group_index * step   # adjusted thrid attribute values. use the medium value of this bin
third_attribute_ajusted = sns_df[third_attribute].min() + group_index * step   # adjusted thrid attribute values. use the left value of this bin.
sns_df['third_attribute_adjusted'] = third_attribute_ajusted

# plot line plot with CI
sns.set(font="Arial", font_scale=1.5, style="ticks")
ax = sns.lineplot(data=sns_df, x="third_attribute_adjusted", y="car_win", label="Car prevalence", color="#e41a1c")
sns.lineplot(data=sns_df, x="third_attribute_adjusted", y="pt_win", ax=ax, label="PT prevalence", color="#377eb8")
sns.lineplot(data=sns_df, x="third_attribute_adjusted", y="car_pt_compl", ax=ax, label="Complementation", color="#4daf4a")

plt.legend(bbox_to_anchor=(1.8, 0.5), frameon=False)

# plot distribution of trips
ax2 = ax.twinx()
sns_df2 = trips_nj_new.copy(True)
sns_df2['euclidean_distance_km'] = sns_df2['euclidean_distance'] / 1000
sns.histplot(data=sns_df2, x="euclidean_distance_km", binwidth=1, ax=ax2, stat="probability", fill=False, kde=True, color="gray", legend=False)

ax.set_xlabel("Travel radius (km)")
ax.set_ylabel("Relationship compostion")
ax2.set_ylabel("Trips proportion")
ax.set_xlim([0, 21])
ax.get_yaxis().set_minor_locator(ticker.AutoMinorLocator(2))
ax2.get_yaxis().set_minor_locator(ticker.AutoMinorLocator(2))

plt.savefig(r"Your output folder path\competition results between car and pt for each OD pair.pdf", facecolor="white", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')