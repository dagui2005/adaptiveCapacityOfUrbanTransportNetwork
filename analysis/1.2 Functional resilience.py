import geopandas as gpd
import matplotlib.pyplot as plt
from matplotlib import ticker
import seaborn as sns
import pandas as pd
import numpy as np

""" 0. Data preparation """
######### 0.1 trips info #############
trips_baseline = pd.read_csv(r"Your baseline folder path\output_trips_wgs84_cleaned.csv", sep=";")

trips_threshold0 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold1 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold2 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold3 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold4 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold5 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold6 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold7 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")
trips_threshold8 = pd.read_csv(r"Your folder path\output_trips_wgs84_cleaned.csv", sep=";")


""" 1. Import structural damage results, operational damage results and functional damage results for all flood scenarios """
struct_damages_df = pd.read_csv(r"Your file path")
operational_damages_df = pd.read_csv(r"Your file path")

################# Functional resilience statistics ######################
threshold = 0
thresholds = []
impassable_trips_prop = []
pt_splits, pt_splits_adjusted = [], []  # Public transportation share, adjusted public transportation share
car_splits, car_splits_adjusted = [], []

for trips in [trips_threshold0, trips_threshold1, trips_threshold2, trips_threshold3, trips_threshold4, trips_threshold5, \
    trips_threshold6, trips_threshold7, trips_threshold8]:
    thresholds.append(threshold)
    impassable_trips_prop.append((len(trips_baseline) - len(trips)) / len(trips_baseline))
    pt_splits.append( len(trips[trips["my_modes"]!="car"]) / len(trips) )
    car_splits.append( 1 - len(trips[trips["my_modes"]!="car"]) / len(trips))
    pt_splits_adjusted.append( len(trips[trips["my_modes"]!="car"]) / len(trips_baseline) )
    car_splits_adjusted.append( len(trips[trips["my_modes"]=="car"]) / len(trips_baseline))
    threshold += 1

function_damages_df = pd.DataFrame()
function_damages_df["T"] = thresholds
function_damages_df["impassable_trips_prop"] = impassable_trips_prop
function_damages_df["pt_splits"] = pt_splits
function_damages_df["car_splits"] = car_splits
function_damages_df["pt_splits_adjusted"] = pt_splits_adjusted
function_damages_df["car_splits_adjusted"] = car_splits_adjusted

# function_damages_df.to_csv(r"Your output file path")

""" 2. travel time """
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)
sns.boxplot(x='threshold', y='duration', ax=ax, hue='modes', palette={"car":sns.color_palette("Set2")[1], "pt":sns.color_palette("Set2")[0]}, width=0.5, linewidth=1, showfliers=False, data=sns_df,
            showmeans=True, meanprops={"marker": "o", "markerfacecolor":"white", "markeredgecolor":"black"})

# In order to keep the straight lines and box plots consistent when plotting, the car's horizontal coordinate is shifted left by 0.1 and the pt's horizontal coordinate is shifted right by 0.1.
ax.plot([i + 0.1 for i in threshold_list], median_duration_pt, color=sns.color_palette("Set2")[0], alpha=1, linewidth=2, linestyle='dashed')
ax.plot([i - 0.1 for i in threshold_list], median_duration_car, color=sns.color_palette("Set2")[1], alpha=1, linewidth=2, linestyle='solid')
ax.legend_.remove()
ax.set_xlabel("$T$ (m)", fontsize=14)
ax.set_ylabel("Travel time (min)", fontsize=14)
plt.yticks(fontsize=14)
plt.xticks(fontsize=14)
plt.savefig(r"Your output file path", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')

""" 3. fraction of passable trips """
threshold = 0
thresholds = []
pt_splits = []  # 公交分担率  分母是 runoff = 0 场景下的需求
car_splits = []   # 小汽车分担率  分母是 runoff = 0 场景下的需求
transfer_times = []  #  平均公共交通换成率
passable_trips_prop = []

for trips in [trips_threshold0, trips_threshold1, trips_threshold2, trips_threshold3, trips_threshold4, trips_threshold5, \
    trips_threshold6, trips_threshold7, trips_threshold8]:
    thresholds.append(threshold)
    pt_splits.append( len(trips[trips["my_modes"]!="car"]) / len(trips_baseline) )
    car_splits.append( len(trips[trips["my_modes"]=="car"]) / len(trips_baseline))
    transfer_times.append( trips[trips.pt_times != 0].pt_times.mean() - 1)   # car 对应 0，不统计；pt_times 是公共交通的使用次数，换乘次数应当减一
    passable_trips_prop.append( len(trips) / len(trips_baseline) )
    threshold += 1

fig = plt.figure()
ax = fig.add_subplot(111)
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
plt.style.use("default")

plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in') 
ax.tick_params(which ='minor', direction='in') 
ax.plot(thresholds, passable_trips_prop, marker="o", fillstyle="none", markersize=10, alpha=1, linewidth=1.5, color="#4C7FBC", label="All")
ax.plot(thresholds, car_splits, marker="^",fillstyle="none", markersize=10, alpha=1, linewidth=1.5, color="#ff7f00", label="Car")
ax.plot(thresholds, pt_splits, marker="s", fillstyle="none", markersize=10, alpha=1, linewidth=1.5, color="#4daf4a", label="PT")

ax.get_yaxis().set_minor_locator(ticker.AutoMinorLocator(2))
ax.set_ylabel("Fraction of passable trips", fontsize=18)
ax.set_xlabel("$T$ (m)", fontsize=18)
plt.xticks(fontsize=18)
plt.yticks(fontsize=18)
plt.ylim([-0.05, 1.05])
lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.3,0.5), fontsize=18, frameon=False)

plt.savefig(r"Your output file path", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')