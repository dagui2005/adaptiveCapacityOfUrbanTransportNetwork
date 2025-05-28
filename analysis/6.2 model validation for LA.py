import pandas as pd
import geopandas as gpd
import seaborn as sns
import ast
import os
import numpy as np
from pyproj import Transformer

import matplotlib.pyplot as plt
from matplotlib.colors import LogNorm
plt.style.use("default")

from scipy.stats import ks_2samp
from shapely import LineString, Point

""" 1 baseline model validation for LA """
""" function: extract data in LA """
def screen_la(date):
    print("\nBegin to deal with ", date, "....")
    # merge the data in the same day
    input_folder = r"your folder\flood scenario\model validation\data\\"  + date
    output_path = r"your folder\flood scenario\model validation\data\\" + date + "_merge_la.csv.gz" 

    # Merge all files
    flow_all = []
    for file in os.listdir(input_folder):
        if file[-3:] == "csv":
            flow_df = pd.read_csv(f'{input_folder}/{file}')
            flow_all.append(flow_df)
    result = pd.concat([x for x in flow_all])
    result.to_csv(output_path, index=False, compression="gzip")
    result.head()
    # geoid 为 tract id (11 位数) int 形式，部分样本数据省略了前面的 0，
    result = result.__deepcopy__()
    result["geoid_o"] = result["geoid_o"].apply(lambda x: repr(x) if len(repr(x)) == 11 else "0" + repr(x))
    result["geoid_d"] = result["geoid_d"].apply(lambda x: repr(x) if len(repr(x)) == 11 else "0" + repr(x))

    # 提取 county geoid (5 位数)  # TODO: 此处可能有问题，Simulation 包含起点或讫点不在 LA 的 trip
    result["county_geoid_o"] = result["geoid_o"].apply(lambda x: x[:5])
    result["county_geoid_d"] = result["geoid_d"].apply(lambda x: x[:5])
    # 提取 state geoid (2 位数)
    # data_20190417["state_geoid_o"] = data_20190417["geoid_o"].apply(lambda x: x[:2])
    # data_20190417["state_geoid_d"] = data_20190417["geoid_d"].apply(lambda x: x[:2])
    # screen data in LA county (geoid = 06037)
    result_la = result[(result["county_geoid_o"] == "06037") & (result["county_geoid_d"] == "06037")]

    # calculate distance
    transformer = Transformer.from_crs("EPSG:4326", "EPSG:3310")
    x1, y1 = transformer.transform(result_la["lat_o"], result_la["lng_o"])   # x is latitude, y is longitude
    x2, y2 = transformer.transform(result_la["lat_d"], result_la["lng_d"])
    dis = [((a - b) ** 2 + (c - d) ** 2) ** 0.5 for a, b, c, d in zip(x1, x2, y1, y2)]   # m
    result_la = result_la.__deepcopy__()
    result_la["dis"] = [i / 1000 for i in dis]   # km
    print("Data ", date, ", Total population flows: ", result_la["pop_flows"].sum())

    result_la.to_csv(output_path, index=False, compression="gzip")


date_baselines = ["2019_01_07", "2019_02_07", "2019_02_27", "2019_11_13", "2019_11_27", "2019_12_16"]
date_floodings = ["2019_01_14", "2019_02_14", "2019_03_06", "2019_11_20", "2019_12_04", "2019_12_23"]

""" function: reform data (重组数据，将原始数据分组到每个km内) """
def reform_safegraph(safegrph_df):
    safegrph_df["radius_label"] = pd.cut(x=safegrph_df['dis'], bins=np.arange(start=0, stop=71, step=1))
    safegrph_df_reform = safegrph_df.groupby(by="radius_label").agg({"pop_flows": "sum"}).reset_index(drop=False)
    safegrph_df_reform["trip_cumsum"] = np.cumsum(safegrph_df_reform["pop_flows"]) / max(np.cumsum(safegrph_df_reform["pop_flows"]))
    safegrph_df_reform.rename(columns={"pop_flows":"trip_num"}, inplace=True)
    safegrph_df_reform['radius_mid'] = [i.mid for i in safegrph_df_reform['radius_label']]
    return safegrph_df_reform

def reform_simulation(simulation_df):
    simulation_df['radius_label'] = pd.cut(x=simulation_df["euclidean_distance"] / 1000, bins=np.arange(start=0, stop=71, step=1))
    simulation_df_reform = simulation_df.groupby(by="radius_label").agg({"trip_id": "count"}).reset_index(drop=False)
    simulation_df_reform["trip_cumsum"] = np.cumsum(simulation_df_reform['trip_id']) / max(np.cumsum(simulation_df_reform['trip_id']))
    simulation_df_reform.rename(columns={'trip_id':'trip_num'}, inplace=True)
    simulation_df_reform['radius_mid'] = [i.mid for i in simulation_df_reform['radius_label']]
    return simulation_df_reform


## other working day before one week of rainy day
result_la_2019_01_07 = pd.read_csv(r"your folder\flood scenario\model validation\data\\" + date_baselines[0] + "_merge_la.csv.gz", compression="gzip")
result_la_2019_02_07 = pd.read_csv(r"your folder\flood scenario\model validation\data\\" + date_baselines[1] + "_merge_la.csv.gz", compression="gzip")
result_la_2019_02_27 = pd.read_csv(r"your folder\flood scenario\model validation\data\\" + date_baselines[2] + "_merge_la.csv.gz", compression="gzip")
result_la_2019_11_13 = pd.read_csv(r"your folder\flood scenario\model validation\data\\" + date_baselines[3] + "_merge_la.csv.gz", compression="gzip")
result_la_2019_11_27 = pd.read_csv(r"your folder\flood scenario\model validation\data\\" + date_baselines[4] + "_merge_la.csv.gz", compression="gzip")
result_la_2019_12_16 = pd.read_csv(r"your folder\flood scenario\model validation\data\\" + date_baselines[5] + "_merge_la.csv.gz", compression="gzip")

# reform data
result_la_2019_01_07_reform = reform_safegraph(safegrph_df=result_la_2019_01_07)
result_la_2019_02_07_reform = reform_safegraph(safegrph_df=result_la_2019_02_07)
result_la_2019_02_27_reform = reform_safegraph(safegrph_df=result_la_2019_02_27)
result_la_2019_11_13_reform = reform_safegraph(safegrph_df=result_la_2019_11_13)
result_la_2019_11_27_reform = reform_safegraph(safegrph_df=result_la_2019_11_27)
result_la_2019_12_16_reform = reform_safegraph(safegrph_df=result_la_2019_12_16)

result_la_all = [result_la_2019_01_07, result_la_2019_02_07, result_la_2019_02_27, result_la_2019_11_13, 
                 result_la_2019_11_27, result_la_2019_12_16]

result_la_all_reform = [result_la_2019_01_07_reform, result_la_2019_02_07_reform, result_la_2019_02_27_reform, result_la_2019_11_13_reform, 
                        result_la_2019_11_27_reform, result_la_2019_12_16_reform]

#　0.2 read baseline trips
# official output trips csv
baseline_trip_df = pd.read_csv(r"your folder\flood scenario\simulaiton\baseline-LACity-10pct\output\output_trips.csv.gz", compression="gzip", sep=";")

# reform data 
baseline_trip_df_reform = reform_simulation(simulation_df=baseline_trip_df)

# add and remove some cols
invalid_columns = ['person', 'trip_number', 'trip_id', 'dep_time', 'trav_time', 'wait_time', 'traveled_distance', 'main_mode', 'longest_distance_mode', 'modes', 'start_activity_type', 'end_activity_type', 'start_facility_id', 'start_link', 'end_facility_id', 'end_link', 'first_pt_boarding_stop', 'last_pt_egress_stop']
baseline_trip_df.drop(columns=invalid_columns, inplace=True)
baseline_trip_df['euclidean_distance_km'] = baseline_trip_df['euclidean_distance'] / 1000

sns.set_theme(font="Arial", font_scale=1.5, style="ticks")
k = 0

for date, df in zip(date_baselines, result_la_all):
    for walking_dis_r in [0]:
        # 绘制频率分布图
        fig, ax = plt.subplots()
        
        # only plot the kde plot
        sns.kdeplot(data=df[(df["dis"] <= 50) & (df["dis"] > walking_dis_r)], 
                    x='dis',
                    ax=ax,
                    weights='pop_flows', 
                    label="Safegraph",
                    color="#1f78b4",
                    )

        sns.kdeplot(data=baseline_trip_df[(baseline_trip_df['euclidean_distance_km'] <= 50) & (baseline_trip_df['euclidean_distance_km'] >= walking_dis_r)], 
                    x="euclidean_distance_km",
                    label="Simulation",
                    color="#e31a1c",
                    )

        # 图形设置
        # plt.title('') 
        plt.xlabel('Travel radius (km)')
        plt.ylabel('Density')
        plt.xlim([0, 50])
        # 绘图技巧：多子图共用一个 legend
        lines = []
        labels = []
        for ax in fig.axes:
            axLine, axLabel = ax.get_legend_handles_labels()
            lines.extend(axLine)
            labels.extend(axLabel)
        fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(0.85,0.7), frameon=False)

        path = r"your folder\flood scenario\model validation\figure\baseline validation pdf using SafeGraph " + date + ".svg"
        
        plt.savefig(path, format='svg', dpi=1200, pad_inches=0.1, bbox_inches='tight')


""" 2. flooding model validation for LA """
def screen_la(data):
    print("\nBegin to deal with ", data, "....")
    # merge the data in the same day
    input_folder = r"your folder\flood scenario\model validation\data\\"  + data
    output_path = r"your folder\flood scenario\model validation\data\\" + data + "_merge_la.csv.gz" 

    # Merge all files
    flow_all = []
    for file in os.listdir(input_folder):
        if file[-3:] == "csv":
            flow_df = pd.read_csv(f'{input_folder}/{file}')
            flow_all.append(flow_df)
    result = pd.concat([x for x in flow_all])
    result.to_csv(output_path, index=False, compression="gzip")
    result.head()
    # geoid 为 tract id (11 位数) int 形式，部分样本数据省略了前面的 0，
    result = result.__deepcopy__()
    result["geoid_o"] = result["geoid_o"].apply(lambda x: repr(x) if len(repr(x)) == 11 else "0" + repr(x))
    result["geoid_d"] = result["geoid_d"].apply(lambda x: repr(x) if len(repr(x)) == 11 else "0" + repr(x))

    # 提取 county geoid (5 位数)
    result["county_geoid_o"] = result["geoid_o"].apply(lambda x: x[:5])
    result["county_geoid_d"] = result["geoid_d"].apply(lambda x: x[:5])
    # 提取 state geoid (2 位数)
    # data_20190417["state_geoid_o"] = data_20190417["geoid_o"].apply(lambda x: x[:2])
    # data_20190417["state_geoid_d"] = data_20190417["geoid_d"].apply(lambda x: x[:2])
    # screen data in LA county (geoid = 06037)
    result_la = result[(result["county_geoid_o"] == "06037") & (result["county_geoid_d"] == "06037")]

    # calculate distance
    transformer = Transformer.from_crs("EPSG:4326", "EPSG:3310")
    x1, y1 = transformer.transform(result_la["lat_o"], result_la["lng_o"])   # x is latitude, y is longitude
    x2, y2 = transformer.transform(result_la["lat_d"], result_la["lng_d"])
    dis = [((a - b) ** 2 + (c - d) ** 2) ** 0.5 for a, b, c, d in zip(x1, x2, y1, y2)]   # m
    result_la = result_la.__deepcopy__()
    result_la["dis"] = [i / 1000 for i in dis]   # km
    print("Data ", data, ", Total population flows: ", result_la["pop_flows"].sum())

    result_la.to_csv(output_path, index=False, compression="gzip")

""" function: reform data (重组数据，将原始数据分组到每个km内) """
def reform_safegraph(safegrph_df):
    safegrph_df["radius_label"] = pd.cut(x=safegrph_df['dis'], bins=np.arange(start=0, stop=71, step=1))
    safegrph_df_reform = safegrph_df.groupby(by="radius_label").agg({"pop_flows": "sum"}).reset_index(drop=False)
    safegrph_df_reform["trip_cumsum"] = np.cumsum(safegrph_df_reform["pop_flows"]) / max(np.cumsum(safegrph_df_reform["pop_flows"]))
    safegrph_df_reform.rename(columns={"pop_flows":"trip_num"}, inplace=True)
    safegrph_df_reform['radius_mid'] = [i.mid for i in safegrph_df_reform['radius_label']]
    return safegrph_df_reform

def reform_simulation(simulation_df):
    simulation_df['radius_label'] = pd.cut(x=simulation_df["euclidean_distance"] / 1000, bins=np.arange(start=0, stop=71, step=1))
    simulation_df_reform = simulation_df.groupby(by="radius_label").agg({"trip_id": "count"}).reset_index(drop=False)
    simulation_df_reform["trip_cumsum"] = np.cumsum(simulation_df_reform['trip_id']) / max(np.cumsum(simulation_df_reform['trip_id']))
    simulation_df_reform.rename(columns={'trip_id':'trip_num'}, inplace=True)
    simulation_df_reform['radius_mid'] = [i.mid for i in simulation_df_reform['radius_label']]
    return simulation_df_reform

date_baselines = ["2019_02_07", "2019_02_27", "2019_11_13", "2019_11_27"]
date_floodings = ["2019_02_14", "2019_03_06", "2019_11_20", "2019_12_04"]
""" 1. read la safegraph data """
walking_dis_r = 0
trips_20190107 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_01_07_merge_la.csv.gz", compression="gzip")
trips_20190114 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_01_14_merge_la.csv.gz", compression="gzip")
trips_20190107 = trips_20190107[trips_20190107['dis'] > walking_dis_r]
trips_20190114 = trips_20190114[trips_20190114['dis'] > walking_dis_r]
trips_20190107_reform = reform_safegraph(safegrph_df=trips_20190107)
trips_20190114_reform = reform_safegraph(safegrph_df=trips_20190114)
print("date ", date_baselines[0], ", baseline, trips: ", trips_20190107_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20190107_reform["trip_num"] * pd.Series([i.mid for i in trips_20190107_reform["radius_label"]]))) / sum(trips_20190107_reform["trip_num"]))
print("date ", date_floodings[0], ", flooding, trips: ", trips_20190114_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20190114_reform["trip_num"] * pd.Series([i.mid for i in trips_20190114_reform["radius_label"]]))) / sum(trips_20190114_reform["trip_num"]))
print()

trips_20190207 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_02_07_merge_la.csv.gz", compression="gzip")
trips_20190214 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_02_14_merge_la.csv.gz", compression="gzip")
trips_20190207 = trips_20190207[trips_20190207['dis'] > walking_dis_r]
trips_20190214 = trips_20190214[trips_20190214['dis'] > walking_dis_r]
trips_20190207_reform = reform_safegraph(safegrph_df=trips_20190207)
trips_20190214_reform = reform_safegraph(safegrph_df=trips_20190214)
print("date ", date_baselines[1], ", baseline, trips: ", trips_20190207_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20190207_reform["trip_num"] * pd.Series([i.mid for i in trips_20190207_reform["radius_label"]]))) / sum(trips_20190207_reform["trip_num"]))
print("date ", date_floodings[1], ", flooding, trips: ", trips_20190214_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20190214_reform["trip_num"] * pd.Series([i.mid for i in trips_20190214_reform["radius_label"]]))) / sum(trips_20190214_reform["trip_num"]))
print()

trips_20190227 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_02_27_merge_la.csv.gz", compression="gzip")
trips_20190306 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_03_06_merge_la.csv.gz", compression="gzip")
trips_20190227 = trips_20190227[trips_20190227['dis'] > walking_dis_r]
trips_20190306 = trips_20190306[trips_20190306['dis'] > walking_dis_r]
trips_20190227_reform = reform_safegraph(safegrph_df=trips_20190227)
trips_20190306_reform = reform_safegraph(safegrph_df=trips_20190306)
print("date ", date_baselines[2], ", baseline, trips: ", trips_20190227_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20190227_reform["trip_num"] * pd.Series([i.mid for i in trips_20190227_reform["radius_label"]]))) / sum(trips_20190227_reform["trip_num"]))
print("date ", date_floodings[2], ", flooding, trips: ", trips_20190306_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20190306_reform["trip_num"] * pd.Series([i.mid for i in trips_20190306_reform["radius_label"]]))) / sum(trips_20190306_reform["trip_num"]))
print()

trips_20191113 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_11_13_merge_la.csv.gz", compression="gzip")
trips_20191120 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_11_20_merge_la.csv.gz", compression="gzip")
trips_20191113 = trips_20191113[trips_20191113['dis'] > walking_dis_r]
trips_20191120 = trips_20191120[trips_20191120['dis'] > walking_dis_r]
trips_20191113_reform = reform_safegraph(safegrph_df=trips_20191113)
trips_20191120_reform = reform_safegraph(safegrph_df=trips_20191120)
print("date ", date_baselines[3], ", baseline, trips: ", trips_20191113_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20191113_reform["trip_num"] * pd.Series([i.mid for i in trips_20191113_reform["radius_label"]]))) / sum(trips_20191113_reform["trip_num"]))
print("date ", date_floodings[3], ", flooding, trips: ", trips_20191120_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20191120_reform["trip_num"] * pd.Series([i.mid for i in trips_20191120_reform["radius_label"]]))) / sum(trips_20191120_reform["trip_num"]))
print()

trips_20191127 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_11_27_merge_la.csv.gz", compression="gzip")
trips_20191204 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_12_04_merge_la.csv.gz", compression="gzip")
trips_20191127 = trips_20191127[trips_20191127['dis'] > walking_dis_r]
trips_20191204 = trips_20191204[trips_20191204['dis'] > walking_dis_r]
trips_20191127_reform = reform_safegraph(safegrph_df=trips_20191127)
trips_20191204_reform = reform_safegraph(safegrph_df=trips_20191204)
print("date ", date_baselines[4], ", baseline, trips: ", trips_20191127_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20191127_reform["trip_num"] * pd.Series([i.mid for i in trips_20191127_reform["radius_label"]]))) / sum(trips_20191127_reform["trip_num"]))
print("date ", date_floodings[4], ", flooding, trips: ", trips_20191204_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20191204_reform["trip_num"] * pd.Series([i.mid for i in trips_20191204_reform["radius_label"]]))) / sum(trips_20191204_reform["trip_num"]))
print()

trips_20191216 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_12_16_merge_la.csv.gz", compression="gzip")
trips_20191223 = pd.read_csv(r"your folder\flood scenario\model validation\data\2019_12_23_merge_la.csv.gz", compression="gzip")
trips_20191216 = trips_20191216[trips_20191216['dis'] > walking_dis_r]
trips_20191223 = trips_20191223[trips_20191223['dis'] > walking_dis_r]
trips_20191216_reform = reform_safegraph(safegrph_df=trips_20191216)
trips_20191223_reform = reform_safegraph(safegrph_df=trips_20191223)
print("date ", date_baselines[5], ", baseline, trips: ", trips_20191216_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20191216_reform["trip_num"] * pd.Series([i.mid for i in trips_20191216_reform["radius_label"]]))) / sum(trips_20191216_reform["trip_num"]))
print("date ", date_floodings[5], ", flooding, trips: ", trips_20191223_reform["trip_num"].sum(), ", mean radius: ", sum((trips_20191223_reform["trip_num"] * pd.Series([i.mid for i in trips_20191223_reform["radius_label"]]))) / sum(trips_20191223_reform["trip_num"]))
print()

""" 3. read simulation data """
########## 读取 输出的原始数据
print("Simulated mobility datasets (ORIGINAL)")

# add and remove some cols
invalid_columns = ['person', 'trip_number', 'trip_id', 'dep_time', 'trav_time', 'wait_time', 'traveled_distance', 'main_mode', 'longest_distance_mode', 'modes', 'start_activity_type', 'end_activity_type', 'start_facility_id', 'start_link', 'end_facility_id', 'end_link', 'first_pt_boarding_stop', 'last_pt_egress_stop']

# baseline
simulation_bl_df = pd.read_csv(r"your folder\flood scenario\simulaiton\baseline-LACity-10pct\output\output_trips.csv.gz", sep=";", compression="gzip")
simulation_bl_df_reform = reform_simulation(simulation_df=simulation_bl_df)
print("Baseline, trip num: ", simulation_bl_df_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_bl_df_reform['trip_num'] * pd.Series([i.mid for i in simulation_bl_df_reform['radius_label']]))) / sum(simulation_bl_df_reform['trip_num']))
simulation_bl_df.drop(columns=invalid_columns, inplace=True)
simulation_bl_df['euclidean_distance_km'] = simulation_bl_df['euclidean_distance'] / 1000

# flooding
simulation_t2 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold2\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t2_reform = reform_simulation(simulation_df=simulation_t2)
print("T=2, trip num: ", simulation_t2_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t2_reform['trip_num'] * pd.Series([i.mid for i in simulation_t2_reform['radius_label']]))) / sum(simulation_t2_reform['trip_num']))
simulation_t2.drop(columns=invalid_columns, inplace=True)
simulation_t2['euclidean_distance_km'] = simulation_t2['euclidean_distance'] / 1000

simulation_t1 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold1\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t1_reform = reform_simulation(simulation_df=simulation_t1)
print("T=1, trip num: ", simulation_t1_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t1_reform['trip_num'] * pd.Series([i.mid for i in simulation_t1_reform['radius_label']]))) / sum(simulation_t1_reform['trip_num']))
simulation_t1.drop(columns=invalid_columns, inplace=True)
simulation_t1['euclidean_distance_km'] = simulation_t1['euclidean_distance'] / 1000

simulation_t0_8 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.8\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_8_reform = reform_simulation(simulation_df=simulation_t0_8)
print("T=0.8, trip num: ", simulation_t0_8_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_8_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_8_reform['radius_label']]))) / sum(simulation_t0_8_reform['trip_num']))
simulation_t0_8.drop(columns=invalid_columns, inplace=True)
simulation_t0_8['euclidean_distance_km'] = simulation_t0_8['euclidean_distance'] / 1000

simulation_t0_6 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.6\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_6_reform = reform_simulation(simulation_df=simulation_t0_6)
print("T=0.6, trip num: ", simulation_t0_6_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_6_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_6_reform['radius_label']]))) / sum(simulation_t0_6_reform['trip_num']))
simulation_t0_6.drop(columns=invalid_columns, inplace=True)
simulation_t0_6['euclidean_distance_km'] = simulation_t0_6['euclidean_distance'] / 1000

simulation_t0_4 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.4\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_4_reform = reform_simulation(simulation_df=simulation_t0_4)
print("T=0.4, trip num: ", simulation_t0_4_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_4_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_4_reform['radius_label']]))) / sum(simulation_t0_4_reform['trip_num']))
simulation_t0_4.drop(columns=invalid_columns, inplace=True)
simulation_t0_4['euclidean_distance_km'] = simulation_t0_4['euclidean_distance'] / 1000

simulation_t0_3 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.3\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_3_reform = reform_simulation(simulation_df=simulation_t0_3)
print("T=0.3, trip num: ", simulation_t0_3_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_3_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_3_reform['radius_label']]))) / sum(simulation_t0_3_reform['trip_num']))
simulation_t0_3.drop(columns=invalid_columns, inplace=True)
simulation_t0_3['euclidean_distance_km'] = simulation_t0_3['euclidean_distance'] / 1000

simulation_t0_2 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.2\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_2_reform = reform_simulation(simulation_df=simulation_t0_2)
print("T=0.2, trip num: ", simulation_t0_2_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_2_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_2_reform['radius_label']]))) / sum(simulation_t0_2_reform['trip_num']))
simulation_t0_2.drop(columns=invalid_columns, inplace=True)
simulation_t0_2['euclidean_distance_km'] = simulation_t0_2['euclidean_distance'] / 1000

simulation_t0_07 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.07\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_07_reform = reform_simulation(simulation_df=simulation_t0_07)
print("T=0.07, trip num: ", simulation_t0_07_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_07_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_07_reform['radius_label']]))) / sum(simulation_t0_07_reform['trip_num']))
simulation_t0_07.drop(columns=invalid_columns, inplace=True)
simulation_t0_07['euclidean_distance_km'] = simulation_t0_07['euclidean_distance'] / 1000

simulation_t0_02 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.02\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_02_reform = reform_simulation(simulation_df=simulation_t0_02)
print("T=0.02, trip num: ", simulation_t0_02_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_02_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_02_reform['radius_label']]))) / sum(simulation_t0_02_reform['trip_num']))
simulation_t0_02.drop(columns=invalid_columns, inplace=True)
simulation_t0_02['euclidean_distance_km'] = simulation_t0_02['euclidean_distance'] / 1000

simulation_t0_01 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.01\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_01_reform = reform_simulation(simulation_df=simulation_t0_01)
print("T=0.01, trip num: ", simulation_t0_01_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_01_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_01_reform['radius_label']]))) / sum(simulation_t0_01_reform['trip_num']))
simulation_t0_01.drop(columns=invalid_columns, inplace=True)
simulation_t0_01['euclidean_distance_km'] = simulation_t0_01['euclidean_distance'] / 1000

simulation_t0_007 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.007\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_007_reform = reform_simulation(simulation_df=simulation_t0_007)
print("T=0.007, trip num: ", simulation_t0_007_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_007_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_007_reform['radius_label']]))) / sum(simulation_t0_007_reform['trip_num']))
simulation_t0_007.drop(columns=invalid_columns, inplace=True)
simulation_t0_007['euclidean_distance_km'] = simulation_t0_007['euclidean_distance'] / 1000

simulation_t0_003 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.003\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_003_reform = reform_simulation(simulation_df=simulation_t0_003)
print("T=0.003, trip num: ", simulation_t0_003_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_003_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_003_reform['radius_label']]))) / sum(simulation_t0_003_reform['trip_num']))
simulation_t0_003.drop(columns=invalid_columns, inplace=True)
simulation_t0_003['euclidean_distance_km'] = simulation_t0_003['euclidean_distance'] / 1000

simulation_t0 = pd.read_csv(r"your folder\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0\output_trips.csv.gz", sep=";", compression="gzip")
simulation_t0_reform = reform_simulation(simulation_df=simulation_t0)
print("T=0, trip num: ", simulation_t0_reform['trip_num'].sum(), ", mean radius: ", sum((simulation_t0_reform['trip_num'] * pd.Series([i.mid for i in simulation_t0_reform['radius_label']]))) / sum(simulation_t0_reform['trip_num']))
simulation_t0.drop(columns=invalid_columns, inplace=True)
simulation_t0['euclidean_distance_km'] = simulation_t0['euclidean_distance'] / 1000

# delete OD pairs which does not exist in baseline scenario.
trips_20190107['od'] = trips_20190107.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20190114['od'] = trips_20190114.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20190114 = trips_20190114[trips_20190114['od'].isin(trips_20190107.od)]
# reform
trips_20190107_reform = reform_safegraph(safegrph_df=trips_20190107)
trips_20190114_reform = reform_safegraph(safegrph_df=trips_20190114)

# delete OD pairs which does not exist in baseline scenario.
trips_20190207['od'] = trips_20190207.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20190214['od'] = trips_20190214.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20190214 = trips_20190214[trips_20190214['od'].isin(trips_20190207.od)]
# reform
trips_20190207_reform = reform_safegraph(safegrph_df=trips_20190207)
trips_20190214_reform = reform_safegraph(safegrph_df=trips_20190214)

# delete OD pairs which does not exist in baseline scenario.
trips_20190227['od'] = trips_20190227.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20190306['od'] = trips_20190306.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20190306 = trips_20190306[trips_20190306['od'].isin(trips_20190227.od)]
# reform
trips_20190227_reform = reform_safegraph(safegrph_df=trips_20190227)
trips_20190306_reform = reform_safegraph(safegrph_df=trips_20190306)

# delete OD pairs which does not exist in baseline scenario.
trips_20191113['od'] = trips_20191113.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20191120['od'] = trips_20191120.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20191120 = trips_20191120[trips_20191120['od'].isin(trips_20191113.od)]
# reform
trips_20191113_reform = reform_safegraph(safegrph_df=trips_20191113)
trips_20191120_reform = reform_safegraph(safegrph_df=trips_20191120)

# delete OD pairs which does not exist in baseline scenario.
trips_20191127['od'] = trips_20191127.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20191204['od'] = trips_20191204.apply(lambda row: str(row['geoid_o']) + "_" + str(row['geoid_d']), axis='columns')
trips_20191204 = trips_20191204[trips_20191204['od'].isin(trips_20191127.od)]
# reform
trips_20191127_reform = reform_safegraph(safegrph_df=trips_20191127)
trips_20191204_reform = reform_safegraph(safegrph_df=trips_20191204)

""" cdf """
sns.set_theme(font="Arial", font_scale=1.5, style="ticks")
k = 0
walking_dis_r = 0
for date, df1, df2 in zip(date_floodings, 
                    [trips_20190207, trips_20190227, trips_20191113, trips_20191127],
                    [trips_20190214, trips_20190306, trips_20191120, trips_20191204]):
    if date != "2019_02_14":
        continue

    T_list = ['1', '0.8', '0.6', '0.4', '0.3', '0.2', '0.07', '0.02', '0.01', '0.007', '0.003', '0']
    for T, df3, df4 in zip(T_list,
                           [simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df, simulation_bl_df],
                           [simulation_t1, simulation_t0_8, simulation_t0_6, simulation_t0_4, simulation_t0_3, simulation_t0_2, simulation_t0_07, simulation_t0_02, simulation_t0_01, simulation_t0_007, simulation_t0_003, simulation_t0]):
        # 绘制频率分布图
        fig, ax = plt.subplots()     
        a = df2[(df2["dis"] <= 50) & (df2["dis"] > walking_dis_r)]
        sns.ecdfplot(data=a, 
                    x='dis',
                    ax=ax,
                    weights='pop_flows', 
                    stat="proportion",
                    label="Safegraph",
                    color="#1f78b4",
                    linewidth=3,
                    )

        b = df4[(df4['euclidean_distance_km'] <= 50) & (df4['euclidean_distance_km'] >= walking_dis_r)]
        sns.ecdfplot(data=b, 
                    x="euclidean_distance_km",
                    stat="proportion",
                    label="Simulation",
                    color="#e31a1c",
                    linewidth=3,
                    )

        # KS 检验
        sample1 = []
        for i in range(len(a)):
            sample1.extend( [a['dis'].iloc[i]] * round((a['pop_flows'].iloc[i])) )
        sample2 = list(b["euclidean_distance_km"])
        print(date, "-", T)
        print(ks_2samp(sample1, sample2))
        
        plt.xlabel('Travel radius (km)')
        plt.ylabel('CDF')
        plt.xlim([0, 50])
        # 绘图技巧：多子图共用一个 legend
        lines = []
        labels = []
        for ax in fig.axes:
            axLine, axLabel = ax.get_legend_handles_labels()
            lines.extend(axLine)
            labels.extend(axLabel)
        fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(0.85,0.5), frameon=False)

        path = r"your folder\flood scenario\model validation\figure\flooding validation cdf using SafeGraph " + date + " T" + T + ".svg"
        plt.savefig(path, format='svg', dpi=1200, pad_inches=0.1, bbox_inches='tight')