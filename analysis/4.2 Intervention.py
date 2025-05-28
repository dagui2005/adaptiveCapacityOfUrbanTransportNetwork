import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import statsmodels.api as sm

""" 0. import data """
# Please preprocess the output data after simulation using python code 0 in advance.
# for threshold = 3
nanjing_t3_r1_df = pd.read_csv(r"Your folder path\demand for each pt subsidy under flooding scenario of threshold3 random seed 1.csv").drop(columns='Unnamed: 0')
nanjing_t3_r2_df = pd.read_csv(r"Your folder path\demand for each pt subsidy under flooding scenario of threshold3 random seed 2.csv.csv").drop(columns='Unnamed: 0')
nanjing_t3_r3_df = pd.read_csv(r"Your folder path\demand for each pt subsidy under flooding scenario of threshold3 random seed 3.csv.csv").drop(columns='Unnamed: 0')

# prepare for fitting
for df in [nanjing_t3_r1_df, nanjing_t3_r2_df, nanjing_t3_r3_df]:
    df['passable_prop'] = df['car_prop'] + df['pt_prop']
    df['pt_split'] = df['pt_prop'] / df['passable_prop']
    df['intercept'] = 1
nanjing_t3_df = pd.concat([nanjing_t3_r1_df, nanjing_t3_r2_df, nanjing_t3_r3_df])

# for threshold = 5
nanjing_t5_r1_df = pd.read_csv(r"Your folder path\demand for each pt subsidy under flooding scenario of threshold5 random=0.csv").drop(columns='Unnamed: 0')
nanjing_t5_r2_df = pd.read_csv(r"Your folder path\demand for each pt subsidy under flooding scenario of threshold5 random seed 2.csv.csv").drop(columns='Unnamed: 0')
nanjing_t5_r3_df = pd.read_csv(r"Your folder path\demand for each pt subsidy under flooding scenario of threshold5 random seed 3.csv.csv").drop(columns='Unnamed: 0')

# prepare for fitting
for df in [nanjing_t5_r1_df, nanjing_t5_r2_df, nanjing_t5_r3_df]:
    df['passable_prop'] = df['car_prop'] + df['pt_prop']
    df['pt_split'] = df['pt_prop'] / df['passable_prop']

nanjing_t5_df = pd.concat([nanjing_t5_r1_df, nanjing_t5_r2_df, nanjing_t5_r3_df])

""" 1. plot the figure for threhold = 3"""
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
fig = plt.figure()
ax = fig.add_subplot(111)
plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in') 
ax.tick_params(which ='minor', direction='in')

g = sns.regplot(x='pt_split', y='passable_prop', data=repeat_trials_r3_mean_df, ci=95, scatter_kws={'s':80}, color='#66c2a5', ax=ax)

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.2,0.5), frameon=False)
ax.set_ylabel("Fraction of passable trips", fontsize=14)
ax.set_xlabel("Public transit share", fontsize=14)
plt.yticks(fontsize=14)
plt.xticks(fontsize=14)
plt.savefig(r"Your folder path\nanjing_trips_t3_PtShare_PassableTrips.pdf", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')

""" 2. liner fiting for threshold3"""
# fit using the average in each pt subsidy scenario.
# randomness induced errors are considered as follow the Normal Distribution
# So, we always negelect the distribution of randomnees-induced errors, and take the average.
repeat_trials_r3_std_df = nanjing_t3_df.groupby("pt_subsidy").agg('std')
repeat_trials_r3_mean_df = nanjing_t3_df.groupby("pt_subsidy").agg('mean')
repeat_trials_r3_mean_df['pt_subsidy'] = repeat_trials_r3_mean_df.index
mod = sm.OLS(repeat_trials_r3_mean_df['passable_prop'], repeat_trials_r3_mean_df[['pt_split', 'intercept']])
res = mod.fit()
print(res.summary())

""" 3. plot figure for threshold5 """
from matplotlib import rcParams
rcParams['font.family'] = 'Arial'
plt.style.use("default")
fig = plt.figure()
ax = fig.add_subplot(111)
plt.tick_params(top='on', right='on', which='both') 
ax.tick_params(which='major', direction='in') 
ax.tick_params(which ='minor', direction='in')

g = sns.regplot(x='pt_split', y='passable_prop', data=repeat_trials_mean_df, scatter_kws={'s':80}, lowess=True, color='#66c2a5', ax=ax)

lines = []
labels = []
for ax in fig.axes:
    axLine, axLabel = ax.get_legend_handles_labels()
    lines.extend(axLine)
    labels.extend(axLabel)
fig.legend(lines, labels, loc = 'center right', bbox_to_anchor=(1.2,0.5), frameon=False)
ax.set_ylabel("Fraction of passable trips", fontsize=14)
ax.set_xlabel("Public transit share", fontsize=14)
plt.yticks(fontsize=14)
plt.xticks(fontsize=14)
plt.savefig(r"Your folder path\nanjing_trips_t5_PtShare_PassableTrips.pdf", format='pdf', dpi=1200, pad_inches=0.1, bbox_inches='tight')
