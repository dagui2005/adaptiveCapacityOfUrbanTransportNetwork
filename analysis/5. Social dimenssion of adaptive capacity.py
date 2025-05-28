import ast
import numpy as np
import pandas as pd
import seaborn as sns

import matplotlib.pyplot as plt
plt.style.use("default")

""" 0. import data and process """
# personal attributes
path = r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【验证】美国洛杉矶\flood scenario\car availability\person attributes\person attributes from la 10pct.txt"
id = []
householdId = []
eduatt = []    # see the following introudction.
hhinc = []    # 家庭收入（Household Income），单位为美元。
race = []   # see the following introudction.
subpopulation = []   # 这里的 subpopulation 没有更新，记得根据 hhnumautos 更新
hhnumautos = []   # 家庭汽车数量（Household Number of Automobiles)
ages = []
genders = []

with open(path) as f:
    for line in f.readlines():
        # 将每行转换为字典
        datas = "{" + line.strip("\n") + "}"
        attr_dict = ast.literal_eval(datas)

        # 过滤掉 subpopulation 为 "freight" 的数据
        if attr_dict.get('subpopulation') == "freight":
            continue

        # 添加到列表中
        id.append(attr_dict.get('id'))
        householdId.append(attr_dict.get('householdId', "NAN"))   # 如果没有 'householdId' 键则使用 "NAN"
        eduatt.append(attr_dict.get('eduatt', "NAN"))  # 如果没有 'eduatt' 键则使用 "NAN"
        hhinc.append(attr_dict.get('hhinc', "NAN"))    # 如果没有 'hhinc' 键则使用 "NAN"
        race.append(attr_dict.get('race', "NAN"))      # 如果没有 'race' 键则使用 "NAN"
        subpopulation.append(attr_dict.get('subpopulation', "NAN"))  # 如果没有 'subpopulation' 键则使用 "NAN"
        hhnumautos.append(attr_dict.get('hhnumautos', "NAN"))  # 如果没有 'hhnumautos' 键则使用 "NAN"
        ages.append(attr_dict.get('age', "NAN"))  # 如果没有 'age' 键则使用 "NAN"
        genders.append(attr_dict.get('gender', "NAN"))  # 如果没有 'gender' 键则使用 "NAN"


person_attr_df = pd.DataFrame()
person_attr_df['person'] = id
person_attr_df['householdId'] = householdId
person_attr_df['eduatt'] = eduatt
person_attr_df['hhinc'] = hhinc
person_attr_df['race'] = race
person_attr_df['hhnumautos'] = [int(i) for i in hhnumautos]
person_attr_df['age'] = ages
person_attr_df['gender'] = genders
person_attr_df['car'] = ["Availible" if int(i) > 0 else "Unavailable" for i in hhnumautos]  

person_attr_df.replace("NAN", np.NAN, inplace=True)
a = len(person_attr_df)
person_attr_df = person_attr_df.dropna()
b = len(person_attr_df)
print("空值的比例:", (a - b ) / b)
person_attr_df.head()


# label the income
# 按照数据的频数等分为四组
person_attr_df["income_rank2"] = pd.qcut(person_attr_df['hhinc'], q=4, labels=False)   # 参考论文 A global map of travel time to cities to assess inequalities in accessibility in 2015



# update the race. We only consider NHAS, HP, NHW, NHB
# NHAS (Non-Hispanic Asian)：非西班牙裔的亚裔，指那些不来自西班牙裔背景的亚洲人种。亚裔包括东亚、东南亚和南亚等地区的人。
# HP (Hispanic)：西班牙裔，指来自拉丁美洲、西班牙或具有拉丁美洲和西班牙文化背景的人。这一族群包括各种族背景的人。
# NHW (Non-Hispanic White)：非西班牙裔的白人，指那些来自欧洲、北非或中东背景的白种人，但不包括具有西班牙裔背景的人。
# NHB (Non-Hispanic Black)：非西班牙裔的黑人，通常指那些非西班牙裔背景的非洲裔美国人或其他黑人种族。
# NHO (Non-Hispanic Other)：非西班牙裔的其他族裔，通常指不属于亚裔、白人、黑人或美洲原住民的其他种族，例如多种族或少见的族裔群体。
# NHAI (Non-Hispanic American Indian or Alaska Native)：非西班牙裔的美国印第安人或阿拉斯加原住民，指那些源于美洲原住民群体的人。
person_attr_df.replace("NHO", "Other", inplace=True)
person_attr_df.replace("NHAI", "Other", inplace=True)

# label the education 
# 重新划分教育水平 （没有教育0, 无高中文凭 1,2，高中文凭3，大学及以上4,5）
def get_my_edu_lab(row):
    if row == 0:
        return 0
    elif row >= 1 and row <= 2:
        return 1
    elif row == 3:
        return 2
    else:
        return 3
eduatt2 = person_attr_df['eduatt'].apply(get_my_edu_lab)

person_attr_df['eduatt2'] = eduatt2



""" 1. Add personal attributes into trips """
# """ function: return all possible trips in this group """
def get_possible_tripIds(person_attr_df, baseline_trips_df, indicator, value):
    # search person ids which meet our requirement
    # if (indicator == "race") or (indicator == "eduatt"):
    personIds = set(person_attr_df[person_attr_df[indicator] == value]['person'])
    # elif indicator == "hhinc":  # the 
        # personIds = # TODO: check the class method
        
    # generate person ids according to trip ids
    # baseline_trips_df['person'] = baseline_trips_df['tripId'].str.split("_", expand=True).rename(columns={0:"person"})["person"]
    tripIds = set(baseline_trips_df[baseline_trips_df['person'].isin(personIds)]['tripId'])
    return tripIds

""" 2.1 race. Here we use the race as an example to show how to plot the sankey diagrams. """ 
my_indicator = "race"
values = ["HP", "NHW", "NHAS", "NHB", "Other"]

""" 1. read trip data """
# trip id generated from input plans (no flood)
la_tripId_baseline_df = pd.read_csv("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\adaptive capacity control experiment\\失效需求机理\\tripIdFromPopulationBase.csv")
la_tripId_baseline_df['person'] = la_tripId_baseline_df['tripId'].str.split("_", expand=True).rename(columns={0:"person"})["person"]
# NOTE: 该 df 不包含 mode 信息，我们不需要使用 mode 信息，因此是否考虑 car availability 对该输入文件无影响
# split the tripId to each group
la_tripId_baseline_df_0 = la_tripId_baseline_df[la_tripId_baseline_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0]))] 
la_tripId_baseline_df_1 = la_tripId_baseline_df[la_tripId_baseline_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))] 
la_tripId_baseline_df_2 = la_tripId_baseline_df[la_tripId_baseline_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2]))] 
la_tripId_baseline_df_3 = la_tripId_baseline_df[la_tripId_baseline_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3]))] 
la_tripId_baseline_df_4 = la_tripId_baseline_df[la_tripId_baseline_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))] 

# trip id generated from flooding input plans (flooding scenarios)
la_input_tripId_t0_3_df = pd.read_csv("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\car availability\\失效需求机理\\tripIdFromPopulationThreshold0.3CarAvail.csv")
# NOTE: 该 df 包含 mode 信息，我们需要更新该文件
# split the tripId to each group
la_input_tripId_t0_3_df_0 = la_input_tripId_t0_3_df[la_input_tripId_t0_3_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0]))] 
la_input_tripId_t0_3_df_1 = la_input_tripId_t0_3_df[la_input_tripId_t0_3_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))]
la_input_tripId_t0_3_df_2 = la_input_tripId_t0_3_df[la_input_tripId_t0_3_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2]))] 
la_input_tripId_t0_3_df_3 = la_input_tripId_t0_3_df[la_input_tripId_t0_3_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3]))] 
la_input_tripId_t0_3_df_4 = la_input_tripId_t0_3_df[la_input_tripId_t0_3_df["tripId"].isin(get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))]


""" 2. 洪水导致的中断的 trip ID """
# 函数：由基准场景的 trip ID 和 洪水场景的输入 trip ID 推断洪水中断的 trip ID
def get_flood_induced_trip_ids(tripId_baseline_df, tripId_input_flooding_df):
    tripId_baseline_df['test'] = 1
    tripId_input_flooding_df['test'] = 1
    flood_induced_df = pd.merge(how='outer', left=tripId_baseline_df, right=tripId_input_flooding_df, on='tripId')
    if flood_induced_df['test_x'].hasnans:
        # 如果 tripId_flooding_df 出现了 tripId_baseline_df 中不存在的 tripId，应当及时检查问题
        print("tripId_input_flooding_df 出现了 tripId_baseline_df 中不存在的 tripId，应当及时检查问题")
    flood_induced_df = flood_induced_df[flood_induced_df['test_y'].isnull()]
    return set(flood_induced_df['tripId'])

# for la
flood_induced_tripIds_la_t03 = get_flood_induced_trip_ids(la_tripId_baseline_df, la_input_tripId_t0_3_df)
# split the tripId to each group.. 注意这是 集合
flood_induced_tripIds_la_t03_0 = flood_induced_tripIds_la_t03 & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
flood_induced_tripIds_la_t03_1 = flood_induced_tripIds_la_t03 & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
flood_induced_tripIds_la_t03_2 = flood_induced_tripIds_la_t03 & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
flood_induced_tripIds_la_t03_3 = flood_induced_tripIds_la_t03 & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
flood_induced_tripIds_la_t03_4 = flood_induced_tripIds_la_t03 & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

""" 3. travel time > 300min 导致的中断的 trip Id """
# 函数: 导入输出的 trip 数据（注意只保留行程链条只有 car 和 pt 的 agents 之后的 trip 数据），输出其中超过 300min 的 trip Id
def get_duration_induced_trip_ids(filePath):
    df = pd.read_csv(filePath, sep=";", compression="gzip")
    df = df[['travel_time(min)', 'person', 'trip_id']]
    duration_induced_tripIds = set(df[df['travel_time(min)'] > 300]['trip_id'])
    return duration_induced_tripIds

# NOTE: 输入的文件 output_trips_wgs84_cleaned.csv 是模型输出的文件，只做了提取 agent 的处理后，剔除不合理模式（主要是 walk）的文件
# for la
# NOTE: car avail 对此处文件有影响，我们需要更新文件
duration_induced_tripIds_la_t03_fa = get_duration_induced_trip_ids(r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【验证】美国洛杉矶\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.3CarAvailA\output_trips_wgs84_delete_by_agents.csv.gz")
# split the tripId to each group.. 注意这是 集合
duration_induced_tripIds_la_t03_fa_0 = duration_induced_tripIds_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
duration_induced_tripIds_la_t03_fa_1 = duration_induced_tripIds_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
duration_induced_tripIds_la_t03_fa_2 = duration_induced_tripIds_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
duration_induced_tripIds_la_t03_fa_3 = duration_induced_tripIds_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
duration_induced_tripIds_la_t03_fa_4 = duration_induced_tripIds_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

duration_induced_tripIds_la_t03_fb = get_duration_induced_trip_ids(r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【验证】美国洛杉矶\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.3CarAvailB\output_trips_wgs84_delete_by_agents.csv.gz")
# split the tripId to each group.. 注意这是 集合
duration_induced_tripIds_la_t03_fb_0 = duration_induced_tripIds_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
duration_induced_tripIds_la_t03_fb_1 = duration_induced_tripIds_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
duration_induced_tripIds_la_t03_fb_2 = duration_induced_tripIds_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
duration_induced_tripIds_la_t03_fb_3 = duration_induced_tripIds_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
duration_induced_tripIds_la_t03_fb_4 = duration_induced_tripIds_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

duration_induced_tripIds_la_t03_f = get_duration_induced_trip_ids(r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【验证】美国洛杉矶\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.3CarAvail\output_trips_wgs84_delete_by_agents.csv.gz")
# split the tripId to each group.. 注意这是 集合
duration_induced_tripIds_la_t03_f_0 = duration_induced_tripIds_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
duration_induced_tripIds_la_t03_f_1 = duration_induced_tripIds_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
duration_induced_tripIds_la_t03_f_2 = duration_induced_tripIds_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
duration_induced_tripIds_la_t03_f_3 = duration_induced_tripIds_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
duration_induced_tripIds_la_t03_f_4 = duration_induced_tripIds_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

""" 4. car trip Id """
# 函数：输入 文件路径，输出 car 和 pt 对应的 trip Id
def getCarPtTripIds(filePath):
    df = pd.read_csv(filePath, sep=";")
    car_trip_ids = set(df[df["my_modes"] == 'car']['trip_id'])
    pt_trip_ids = set(df[df["my_modes"] == 'pt']['trip_id'])
    return car_trip_ids, pt_trip_ids

# for la
# NOTE: car avail 对此处文件有影响，我们需要更新文件
car_trip_ids_la_t03_fa, pt_trip_ids_la_t03_fa = getCarPtTripIds(r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【验证】美国洛杉矶\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.3CarAvailA\output_trips_wgs84_deleted_by_agents_cleaned.csv.gz")
# split the tripId to each group.. 注意这是 集合
car_trip_ids_la_t03_fa_0 = car_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
car_trip_ids_la_t03_fa_1 = car_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
car_trip_ids_la_t03_fa_2 = car_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
car_trip_ids_la_t03_fa_3 = car_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
car_trip_ids_la_t03_fa_4 = car_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))
pt_trip_ids_la_t03_fa_0 = pt_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
pt_trip_ids_la_t03_fa_1 = pt_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
pt_trip_ids_la_t03_fa_2 = pt_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
pt_trip_ids_la_t03_fa_3 = pt_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
pt_trip_ids_la_t03_fa_4 = pt_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

car_trip_ids_la_t03_fb, pt_trip_ids_la_t03_fb = getCarPtTripIds(r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【验证】美国洛杉矶\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.3CarAvailB\output_trips_wgs84_deleted_by_agents_cleaned.csv.gz")
# split the tripId to each group.. 注意这是 集合
car_trip_ids_la_t03_fb_0 = car_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
car_trip_ids_la_t03_fb_1 = car_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
car_trip_ids_la_t03_fb_2 = car_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
car_trip_ids_la_t03_fb_3 = car_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
car_trip_ids_la_t03_fb_4 = car_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))
pt_trip_ids_la_t03_fb_0 = pt_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
pt_trip_ids_la_t03_fb_1 = pt_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
pt_trip_ids_la_t03_fb_2 = pt_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
pt_trip_ids_la_t03_fb_3 = pt_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
pt_trip_ids_la_t03_fb_4 = pt_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

car_trip_ids_la_t03_f, pt_trip_ids_la_t03_f = getCarPtTripIds(r"D:\【学术】\【研究生】\【方向】多模式交通网络韧性-new floods data\【验证】美国洛杉矶\flood scenario\simulaiton\flood-LACity-50th-10pct\threshold0.3CarAvail\output_trips_wgs84_deleted_by_agents_cleaned.csv.gz")
# split the tripId to each group.. 注意这是 集合
car_trip_ids_la_t03_f_0 = car_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
car_trip_ids_la_t03_f_1 = car_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
car_trip_ids_la_t03_f_2 = car_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
car_trip_ids_la_t03_f_3 = car_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
car_trip_ids_la_t03_f_4 = car_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))
pt_trip_ids_la_t03_f_0 = pt_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
pt_trip_ids_la_t03_f_1 = pt_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
pt_trip_ids_la_t03_f_2 = pt_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
pt_trip_ids_la_t03_f_3 = pt_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
pt_trip_ids_la_t03_f_4 = pt_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

""" 5. unreached trip ids """
la_trip_ids_baseline = set(la_tripId_baseline_df['tripId'])
# for la
unreached_trip_ids_la_t03_fa = la_trip_ids_baseline - flood_induced_tripIds_la_t03 - duration_induced_tripIds_la_t03_fa - car_trip_ids_la_t03_fa - pt_trip_ids_la_t03_fa
# split the tripId to each group.. 注意这是 集合
unreached_trip_ids_la_t03_fa_0 = unreached_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
unreached_trip_ids_la_t03_fa_1 = unreached_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
unreached_trip_ids_la_t03_fa_2 = unreached_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
unreached_trip_ids_la_t03_fa_3 = unreached_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
unreached_trip_ids_la_t03_fa_4 = unreached_trip_ids_la_t03_fa & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

unreached_trip_ids_la_t03_fb = la_trip_ids_baseline - flood_induced_tripIds_la_t03 - duration_induced_tripIds_la_t03_fb - car_trip_ids_la_t03_fb - pt_trip_ids_la_t03_fb
# split the tripId to each group.. 注意这是 集合
unreached_trip_ids_la_t03_fb_0 = unreached_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
unreached_trip_ids_la_t03_fb_1 = unreached_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
unreached_trip_ids_la_t03_fb_2 = unreached_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
unreached_trip_ids_la_t03_fb_3 = unreached_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
unreached_trip_ids_la_t03_fb_4 = unreached_trip_ids_la_t03_fb & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))

unreached_trip_ids_la_t03_f = la_trip_ids_baseline - flood_induced_tripIds_la_t03 - duration_induced_tripIds_la_t03_f - car_trip_ids_la_t03_f - pt_trip_ids_la_t03_f
# split the tripId to each group.. 注意这是 集合
unreached_trip_ids_la_t03_f_0 = unreached_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[0])) 
unreached_trip_ids_la_t03_f_1 = unreached_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[1]))
unreached_trip_ids_la_t03_f_2 = unreached_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[2])) 
unreached_trip_ids_la_t03_f_3 = unreached_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[3])) 
unreached_trip_ids_la_t03_f_4 = unreached_trip_ids_la_t03_f & (get_possible_tripIds(person_attr_df=person_attr_df, baseline_trips_df=la_tripId_baseline_df, indicator=my_indicator, value=values[4]))


""" 绘制桑基图 """
# 函数：输入 fa 实验的五个部分的 trip ids，fb 的，f 的，绘制从 Fa --> Fb ---- > F 的桑基图
def plot_sankey_opt(trip_ids_list_fa, trip_ids_list_fb, trip_ids_list_f, out_file_path):
    """ 
    trip_ids_list_fa: 依次包含 Inaccessible, unreached, travel time induecd, pt, car 的 trip set
    """
    links = []
    # Fa --> Fb   
    print("/* from Fa (marked as 'xxx') to Fb (marked as 'xxx ')*/")
    print(["Inaccessible trips",  " ",  len(trip_ids_list_fa[0] & trip_ids_list_fb[0])], ",")
    for fa_set, name1 in zip(trip_ids_list_fa[1:], ['Unreached trips', 'Unaccepted trips', 'PT trips', 'Car trips']):
        for fb_set, name2 in zip(trip_ids_list_fb[1:], ["   ", "    ", "     ", "      "]):
            links.append({"source": name1, "target": name2, "value": len(fa_set & fb_set)})   # 元素交集即为 Link 值
            print([name1,  name2,  len(fa_set & fb_set)], ",")
        # print()
    # Fb --> F
    print()
    print("/* from Fb (marked as 'xxx ') to F (marked as ' xxx') */")
    print([" ",  "  ",  len(trip_ids_list_fb[0] & trip_ids_list_f[0])], ",")  
    for fb_set, name2 in zip(trip_ids_list_fb[1:], ["   ", "    ", "     ", "      "]):
        for f_set, name3 in zip(trip_ids_list_f[1:], ["       ", "        ", "         ", "          "]):
            links.append({"source": name2, "target": name3, "value": len(fb_set & f_set)})   # 元素交集即为 Link 值
            print([name2,  name3,  len(fb_set & f_set)], ",")
    
    # 使用 google chart 绘制桑基图


flood_induced_tripIds_la_t03_all = [flood_induced_tripIds_la_t03_0, flood_induced_tripIds_la_t03_1, flood_induced_tripIds_la_t03_2, flood_induced_tripIds_la_t03_3, flood_induced_tripIds_la_t03_4]

unreached_trip_ids_la_t03_fa_all = [unreached_trip_ids_la_t03_fa_0, unreached_trip_ids_la_t03_fa_1, unreached_trip_ids_la_t03_fa_2, unreached_trip_ids_la_t03_fa_3, unreached_trip_ids_la_t03_fa_4]
unreached_trip_ids_la_t03_fb_all = [unreached_trip_ids_la_t03_fb_0, unreached_trip_ids_la_t03_fb_1, unreached_trip_ids_la_t03_fb_2, unreached_trip_ids_la_t03_fb_3, unreached_trip_ids_la_t03_fb_4]
unreached_trip_ids_la_t03_f_all = [unreached_trip_ids_la_t03_f_0, unreached_trip_ids_la_t03_f_1, unreached_trip_ids_la_t03_f_2, unreached_trip_ids_la_t03_f_3, unreached_trip_ids_la_t03_f_4]

duration_induced_tripIds_la_t03_fa_all = [duration_induced_tripIds_la_t03_fa_0, duration_induced_tripIds_la_t03_fa_1, duration_induced_tripIds_la_t03_fa_2, duration_induced_tripIds_la_t03_fa_3, duration_induced_tripIds_la_t03_fa_4]
duration_induced_tripIds_la_t03_fb_all = [duration_induced_tripIds_la_t03_fb_0, duration_induced_tripIds_la_t03_fb_1, duration_induced_tripIds_la_t03_fb_2, duration_induced_tripIds_la_t03_fb_3, duration_induced_tripIds_la_t03_fb_4]
duration_induced_tripIds_la_t03_f_all = [duration_induced_tripIds_la_t03_f_0, duration_induced_tripIds_la_t03_f_1, duration_induced_tripIds_la_t03_f_2, duration_induced_tripIds_la_t03_f_3, duration_induced_tripIds_la_t03_f_4]

pt_trip_ids_la_t03_fa_all = [pt_trip_ids_la_t03_fa_0, pt_trip_ids_la_t03_fa_1, pt_trip_ids_la_t03_fa_2, pt_trip_ids_la_t03_fa_3, pt_trip_ids_la_t03_fa_4]
pt_trip_ids_la_t03_fb_all = [pt_trip_ids_la_t03_fb_0, pt_trip_ids_la_t03_fb_1, pt_trip_ids_la_t03_fb_2, pt_trip_ids_la_t03_fb_3, pt_trip_ids_la_t03_fb_4]
pt_trip_ids_la_t03_f_all = [pt_trip_ids_la_t03_f_0, pt_trip_ids_la_t03_f_1, pt_trip_ids_la_t03_f_2, pt_trip_ids_la_t03_f_3, pt_trip_ids_la_t03_f_4]

car_trip_ids_la_t03_fa_all = [car_trip_ids_la_t03_fa_0, car_trip_ids_la_t03_fa_1, car_trip_ids_la_t03_fa_2, car_trip_ids_la_t03_fa_3, car_trip_ids_la_t03_fa_4]
car_trip_ids_la_t03_fb_all = [car_trip_ids_la_t03_fb_0, car_trip_ids_la_t03_fb_1, car_trip_ids_la_t03_fb_2, car_trip_ids_la_t03_fb_3, car_trip_ids_la_t03_fb_4]
car_trip_ids_la_t03_f_all = [car_trip_ids_la_t03_f_0, car_trip_ids_la_t03_f_1, car_trip_ids_la_t03_f_2, car_trip_ids_la_t03_f_3, car_trip_ids_la_t03_f_4]


# la 
# t = 0.3 

# for group k
k = 0
print("For group ", k, " , ", values[k])
print("\n")
trip_ids_list_fa_la_t03 = [flood_induced_tripIds_la_t03_all[k], unreached_trip_ids_la_t03_fa_all[k], duration_induced_tripIds_la_t03_fa_all[k], pt_trip_ids_la_t03_fa_all[k], car_trip_ids_la_t03_fa_all[k]]
trip_ids_list_fb_la_t03 = [flood_induced_tripIds_la_t03_all[k], unreached_trip_ids_la_t03_fb_all[k], duration_induced_tripIds_la_t03_fb_all[k], pt_trip_ids_la_t03_fb_all[k], car_trip_ids_la_t03_fb_all[k]]
trip_ids_list_f_la_t03 = [flood_induced_tripIds_la_t03_all[k], unreached_trip_ids_la_t03_f_all[k], duration_induced_tripIds_la_t03_f_all[k], pt_trip_ids_la_t03_f_all[k], car_trip_ids_la_t03_f_all[k]]
output_file_path = r""
# plot_sankey(trip_ids_list_fa=trip_ids_list_fa_la_t03, trip_ids_list_fb=trip_ids_list_fb_la_t03, trip_ids_list_f=trip_ids_list_f_la_t03, out_file_path=output_file_path)
plot_sankey_opt(trip_ids_list_fa=trip_ids_list_fa_la_t03, trip_ids_list_fb=trip_ids_list_fb_la_t03, trip_ids_list_f=trip_ids_list_f_la_t03, out_file_path=output_file_path)
