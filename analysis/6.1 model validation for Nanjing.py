import os
import pandas as pd
import matplotlib.pyplot as plt
plt.style.use("default")

import seaborn as sns
import numpy as np
from collections import defaultdict

import CoordinatesConverter as cc
from scipy.stats import ks_2samp


""" 0. import data """
""" 0.1 empirical data """
# 20240711
ds,o_adcode,d_adcode,source_grid,target_grid,str_time,end_time,vehicle_type,vehicle_size,energy_type,o_xy,d_xy,cnt = [], [], [], [], [], [], [], [], [], [], [], [], []
path = r"your folder\data amap\OD数据分天存储\20240711.txt"
with open(path) as f:
    print(f.readline())
    k = 0
    while f.readable():
        if k % 1000000 == 0:
            print("Begin to deal with the ", k, "th row...")
        datas = f.readline().strip("\n").split(",")
        if len(datas) == 1:  # 若数据为空行，则代表已经到达尾行
            break
        ds.append(datas[0])    # 日期，格式为YYYYMMDD，如20230301
        o_adcode.append(datas[1])    # 起点城市代码(区县级)
        d_adcode.append(datas[2])    # 终点城市代码(区县级)
        source_grid.append(datas[3])    # 起点网格ID（100 米）
        target_grid.append(datas[4])    # 终点网格ID（100 米）
        str_time.append(datas[5])   # 开始时间，间隔为15 分钟，可能为空   1215 表示 12:15
        end_time.append(datas[6])   # 结束时间，间隔为15 分钟，可能为空
        vehicle_type.append(datas[7])   # 汽车类型，1 是货车，0 是汽车
        vehicle_size.append(datas[8])   # 汽车型号，重型货车、中型货车、小型货车等
        energy_type.append(datas[9])   # 是否为电车，1 为电车，0 为油车
        o_xy.append(datas[10])   # 起点网格中心点经纬度(GCJ-02 坐标系)
        d_xy.append(datas[11])   # 终点网格中心点经纬度(GCJ-02 坐标系)
        cnt.append(datas[12])   # 数量

        k += 1

# 20240718
path = r"your folder\data amap\OD数据分天存储\20240718.txt"
with open(path) as f:
    print(f.readline())
    k = 0
    while f.readable():
        if k % 1000000 == 0:
            print("Begin to deal with the ", k, "th row...")
        datas = f.readline().strip("\n").split(",")
        if len(datas) == 1:  # 若数据为空行，则代表已经到达尾行
            break
        ds.append(datas[0])    # 日期，格式为YYYYMMDD，如20230301
        o_adcode.append(datas[1])    # 起点城市代码(区县级)
        d_adcode.append(datas[2])    # 终点城市代码(区县级)
        source_grid.append(datas[3])    # 起点网格ID（100 米）
        target_grid.append(datas[4])    # 终点网格ID（100 米）
        str_time.append(datas[5])   # 开始时间，间隔为15 分钟，可能为空
        end_time.append(datas[6])   # 结束时间，间隔为15 分钟，可能为空
        vehicle_type.append(datas[7])   # 汽车类型，1 是货车，0 是汽车
        vehicle_size.append(datas[8])   # 汽车型号，重型货车、中型货车、小型货车等
        energy_type.append(datas[9])   # 是否为电车，1 为电车，0 为油车
        o_xy.append(datas[10])   # 起点网格中心点经纬度(GCJ-02 坐标系)
        d_xy.append(datas[11])   # 终点网格中心点经纬度(GCJ-02 坐标系)
        cnt.append(datas[12])   # 数量

        k += 1

od_df_amap = pd.DataFrame()
od_df_amap['ds'] = ds
od_df_amap['o_adcode'] = o_adcode
od_df_amap['d_adcode'] = d_adcode
od_df_amap['source_grid'] = source_grid
od_df_amap['target_grid'] = target_grid 
od_df_amap['str_time'] = str_time
od_df_amap['end_time'] = end_time
od_df_amap['vehicle_type'] = vehicle_type
od_df_amap['vehicle_size'] = vehicle_size
od_df_amap['energy_type'] = energy_type
od_df_amap['o_xy'] = o_xy
od_df_amap['d_xy'] = d_xy
od_df_amap['cnt'] = pd.to_numeric(cnt)

""" distance calculation """
od_df_amap['ox'] = pd.to_numeric(od_df_amap['o_xy'].apply(lambda row: row.split('_')[0]))   # CJ-02
od_df_amap['oy'] = pd.to_numeric(od_df_amap['o_xy'].apply(lambda row: row.split('_')[1]))
od_df_amap['dx'] = pd.to_numeric(od_df_amap['d_xy'].apply(lambda row: row.split('_')[0]))   # CJ-02
od_df_amap['dy'] = pd.to_numeric(od_df_amap['d_xy'].apply(lambda row: row.split('_')[1]))

od_df_amap['rad'] = od_df_amap.apply(lambda row: cc.CalDistance(row['ox'], row['oy'], row['dx'], row['dy']), axis='columns')  # km

""" duration calculation """
od_df_amap['str_time'] = od_df_amap['str_time'].apply(lambda x: int(x[:-2]) * 60 + int(x[-2:]))  # min
od_df_amap['end_time'] = od_df_amap['end_time'].apply(lambda x: int(x[:-2]) * 60 + int(x[-2:]))  # min

od_df_amap['dur_time'] = od_df_amap['end_time'] - od_df_amap['str_time']

""" speed calculation """
dur_time_modified = od_df_amap['dur_time'].replace([0], 7.5)  # assume 0-15min 的trips 7.5 min
od_df_amap['speed'] = od_df_amap['rad'] * 1.4 / (dur_time_modified / 60)  # non linear ecoffcient 1.4  # km/h

""" data clean """
# # clean 
od_df_amap_cleaned = od_df_amap[od_df_amap['speed'] <= 120]
od_df_amap_cleaned = od_df_amap_cleaned[(od_df_amap_cleaned['dur_time'] >= 0) & (od_df_amap_cleaned['dur_time'] <= 300)]
od_df_amap_cleaned = od_df_amap_cleaned[(od_df_amap_cleaned['rad'] > 0) & (od_df_amap_cleaned['rad'] < 50)]
od_df_amap_cleaned = od_df_amap_cleaned[(od_df_amap_cleaned['o_adcode'].map(lambda x:x[:4]) == "3201") & (od_df_amap_cleaned['d_adcode'].map(lambda x:x[:4]) == "3201")]   # both origin and destination locates in nanjing
od_df_amap_cleaned = od_df_amap_cleaned[~((od_df_amap_cleaned['ox'] >= 137.8347) | (od_df_amap_cleaned['dx'] <= 72.004) | (od_df_amap_cleaned['oy'] <= 0.8293) | (od_df_amap_cleaned['dy'] >= 55.8271))]
od_df_amap_cleaned = od_df_amap_cleaned[(od_df_amap_cleaned['vehicle_type'] == '0')].reset_index(drop=True)

""" 0.2 import simulation data """
# simulation data
base_sim_trip_df = pd.read_csv(r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【数据】交通仿真\无洪水\output01_demand.noex20.0.27.100pct\output_trips_wgs84_cleaned.csv", sep=";")
base_sim_trip_df["euclidean_distance"] = base_sim_trip_df["euclidean_distance"] / 1000  # km
base_sim_trip_df["traveled_distance"] = base_sim_trip_df["traveled_distance"] / 1000  # km

trips_sim_threshold0 = pd.read_csv(r"your folder\有洪水\threshold0.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold1 = pd.read_csv(r"your folder\有洪水\threshold1.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold2 = pd.read_csv(r"your folder\有洪水\threshold2.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold3 = pd.read_csv(r"your folder\有洪水\threshold3.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold4 = pd.read_csv(r"your folder\有洪水\threshold4.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold5 = pd.read_csv(r"your folder\有洪水\threshold5.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold6 = pd.read_csv(r"your folder\有洪水\threshold6.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold7 = pd.read_csv(r"your folder\有洪水\threshold7.0m\output_trips_wgs84_cleaned.csv", sep=";")
trips_sim_threshold8 = pd.read_csv(r"your folder\有洪水\threshold8.0m\output_trips_wgs84_cleaned.csv", sep=";")

trips_list = [trips_sim_threshold0, trips_sim_threshold1, trips_sim_threshold2, trips_sim_threshold3, trips_sim_threshold4, trips_sim_threshold5, trips_sim_threshold6, trips_sim_threshold7, trips_sim_threshold8]
for df in trips_list:
    df["euclidean_distance"] = df["euclidean_distance"] / 1000  # km
    df["traveled_distance"] = df["traveled_distance"] / 1000  # km

""" 1. baseline validation """
########### cdf only consdier car
df1 = od_df_amap_cleaned[(od_df_amap_cleaned['ds'] == '20240718')]
df1 = df1.sort_values(by="rad", ascending=True).reset_index(drop=True)
df1["trip_num_cusum"] = np.cumsum(df1["cnt"]) / max(np.cumsum(df1["cnt"]))

# df2 = base_sim_trip_df[base_sim_trip_df['my_modes'] == "car"]
df2 = base_sim_trip_df.copy(deep=True)
df2 = df2.sort_values(by="euclidean_distance", ascending=True).reset_index(drop=True)
df2['trip_num'] = 1
df2["trip_cusum"] = np.cumsum(df2["trip_num"]) / max(np.cumsum(df2["trip_num"]))

sns.set_theme(font="Arial", font_scale=1.5, style="ticks")
fig = plt.figure()
ax = fig.add_subplot(111)
# Amap data
ax.plot(df1["dur_time"], df1["trip_num_cusum"], label="Amap", color="#1f78b4", linewidth=3)

# simulation
ax.plot(df2["travel_time(min)"], df2["trip_cusum"], label="Simulation", color="#e31a1c", linewidth=3)

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
# fig.legend(lines, labels, loc = 'lower right', frameon=False)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(0.9,0.5), frameon=False)

plt.ylabel("CDF")
plt.xlabel("Travel time (min)")
path = r"your folder\validation\baseline validation using Amap (CDF of travel time).svg"
plt.savefig(path, format='svg', dpi=1200, pad_inches=0.1, bbox_inches='tight')

""" 3. flooding validation """
########### cdf only consdier car
# baseline Amap
df1 = od_df_amap_cleaned[(od_df_amap_cleaned['ds'] == '20240718') & (od_df_amap_cleaned['rad'] <= 20)]
df1 = df1.sort_values(by="dur_time", ascending=True).reset_index(drop=True)
df1["trip_num_cusum"] = np.cumsum(df1["cnt"]) / max(np.cumsum(df1["cnt"]))

# baseline simulation
df2 = base_sim_trip_df[base_sim_trip_df['my_modes'] == "car"]
df2 = df2.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2['trip_num'] = 1
df2["trip_cusum"] = np.cumsum(df2["trip_num"]) / max(np.cumsum(df2["trip_num"]))

# flooding Amap
df1_flood = od_df_amap_cleaned[(od_df_amap_cleaned['ds'] == '20240711') & (od_df_amap_cleaned['rad'] <= 20)]
df1_flood = df1_flood.sort_values(by="dur_time", ascending=True).reset_index(drop=True)
df1_flood["trip_num_cusum"] = np.cumsum(df1_flood["cnt"]) / max(np.cumsum(df1_flood["cnt"]))

# flooding simulation
df2_T0 = trips_sim_threshold0[trips_sim_threshold0['my_modes'] == "car"]
df2_T0 = df2_T0.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T0['trip_num'] = 1
df2_T0["trip_cusum"] = np.cumsum(df2_T0["trip_num"]) / max(np.cumsum(df2_T0["trip_num"]))

# flooding simulation
df2_T1 = trips_sim_threshold1[trips_sim_threshold1['my_modes'] == "car"]
df2_T1 = df2_T1.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T1['trip_num'] = 1
df2_T1["trip_cusum"] = np.cumsum(df2_T1["trip_num"]) / max(np.cumsum(df2_T1["trip_num"]))

# flooding simulation
df2_T2 = trips_sim_threshold2[trips_sim_threshold2['my_modes'] == "car"]
df2_T2 = df2_T2.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T2['trip_num'] = 1
df2_T2["trip_cusum"] = np.cumsum(df2_T2["trip_num"]) / max(np.cumsum(df2_T2["trip_num"]))

# flooding simulation
df2_T3 = trips_sim_threshold3[trips_sim_threshold3['my_modes'] == "car"]
df2_T3 = df2_T3.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T3['trip_num'] = 1
df2_T3["trip_cusum"] = np.cumsum(df2_T3["trip_num"]) / max(np.cumsum(df2_T3["trip_num"]))

# flooding simulation
df2_T4 = trips_sim_threshold4[trips_sim_threshold4['my_modes'] == "car"]
df2_T4 = df2_T4.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T4['trip_num'] = 1
df2_T4["trip_cusum"] = np.cumsum(df2_T4["trip_num"]) / max(np.cumsum(df2_T4["trip_num"]))

# flooding simulation
df2_T5 = trips_sim_threshold5[trips_sim_threshold5['my_modes'] == "car"]
df2_T5 = df2_T5.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T5['trip_num'] = 1
df2_T5["trip_cusum"] = np.cumsum(df2_T5["trip_num"]) / max(np.cumsum(df2_T5["trip_num"]))

# flooding simulation
df2_T6 = trips_sim_threshold6[trips_sim_threshold6['my_modes'] == "car"]
df2_T6 = df2_T6.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T6['trip_num'] = 1
df2_T6["trip_cusum"] = np.cumsum(df2_T6["trip_num"]) / max(np.cumsum(df2_T6["trip_num"]))

# flooding simulation
df2_T7 = trips_sim_threshold7[trips_sim_threshold7['my_modes'] == "car"]
df2_T7 = df2_T7.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T7['trip_num'] = 1
df2_T7["trip_cusum"] = np.cumsum(df2_T7["trip_num"]) / max(np.cumsum(df2_T7["trip_num"]))

# flooding simulation
df2_T8 = trips_sim_threshold8[trips_sim_threshold8['my_modes'] == "car"]
df2_T8 = df2_T8.sort_values(by="travel_time(min)", ascending=True).reset_index(drop=True)
df2_T8['trip_num'] = 1
df2_T8["trip_cusum"] = np.cumsum(df2_T8["trip_num"]) / max(np.cumsum(df2_T8["trip_num"]))

# 只考虑晴天中出现的 OD
df1['od'] = df1.apply(lambda row: row['source_grid'] + 'To' + row['target_grid'], axis="columns")
df1_flood['od'] = df1_flood.apply(lambda row: row['source_grid'] + "To" + row['target_grid'], axis="columns")

df1_flood_cleaned = df1_flood[df1_flood['od'].isin(set(df1['od']))]

sns.set_theme(font="Arial", font_scale=1.5, style="ticks")

for T, df2_flood in zip([0, 1, 2, 3, 4, 5, 6, 7, 8], 
                        [df2_T0, df2_T1, df2_T2, df2_T3, df2_T4, df2_T5, df2_T6, df2_T7, df2_T8]):
    fig = plt.figure()
    ax = fig.add_subplot(111)
    # Amap data (Baseline)
    # ax.plot(df1["dur_time"], df1["trip_num_cusum"], label="Baseline (Amap)", color="#1f78b4", linewidth=3, linestyle=":")
    
    # Amap data (Flooding)
    ax.plot(df1_flood["dur_time"], df1_flood["trip_num_cusum"], label="Amap", color="#1f78b4", linewidth=3)

    # simulation (Baseline)
    # ax.plot(df2["travel_time(min)"], df2["trip_cusum"], label="Baseline (Simulation)", color="#e31a1c", linewidth=3, linestyle=":")
    
    # simulation (Flooding)
    ax.plot(df2_flood["travel_time(min)"], df2_flood["trip_cusum"], label="Simulation", color="#e31a1c", linewidth=3)

    # 绘图技巧：多子图共用一个 legend
    lines = []
    labels = []
    for ax in fig.axes:
        axLine, axLabel = ax.get_legend_handles_labels()
        lines.extend(axLine)
        labels.extend(axLabel)
    fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(0.9,0.5), frameon=False)

    plt.ylabel("CDF")
    plt.xlabel("Travel time (min)")

    # 计算 KS value
    sample1 = []
    for i in range(len(df1_flood)):
        sample1.extend( [df1_flood['dur_time'].iloc[i]] * round((df1_flood['cnt'].iloc[i])) )
    sample2 = list(df2_flood["travel_time(min)"])
    print(T)
    print(ks_2samp(sample1, sample2))
    path = r"your folder\validation\flooding validation using Amap (CDF of travel time) T" + str(T) + ".svg"
    plt.savefig(path, format='svg', dpi=1200, pad_inches=0.1, bbox_inches='tight')