"""
@author: Chunhong Li'21.0702
@Description: 生成 demand
"""

from numpy.lib.shape_base import column_stack
import pandas as pd
import numpy as np
import math
import sys
import matplotlib.pyplot as plt

""" 1.WGS84坐标系转换为web墨卡托投影,单位由经纬度转为米 """
""" web墨卡托即将椭球视为圆球 """
def wgs84toWebMercator(lon, lat):
    x = lon*20037508.342789/180
    y = math.log(math.tan((90+lat)*math.pi/360))/(math.pi/180)
    y = y * 20037508.34789/180
    return x, y


""" 2.计算两个网格质心间的距离 """
def calDistance(x1, y1, x2, y2):
    return math.sqrt((x1-x2)**2+(y1-y2)**2)

""" 3.模式选择概率   (南京市政府调查问卷，精度较差不再使用)"""
# def calModalProb(distance):
    # if distance <= 5000:
    #     # 各个模式选择概率归一化
    #     probSum = 30.4+60.8+9.6+32.8+33.6+21.6+19.2
    #     carProb = (21.6+19.2)/probSum  # 私家车+合乘
    #     busProb = 32.8/probSum
    #     subwayProb = 33.6/probSum
    # elif distance > 5000:
    #     probSum = 8.8+33.6+10.4+44.8+64.0+33.6+32.8
    #     carProb = (33.6+32.8)/probSum
    #     busProb = 44.8/probSum
    #     subwayProb = 64.0/probSum

    # return carProb, busProb, subwayProb

""" 3.模式选择概率  (18年 交通运输系统工程与信息)""" 
def calModalProb(distance):  # 距离单位 km
    distance = distance/1000
    pWalk = 1.087*(distance**0.002316)*math.exp(-3.769*distance)
    pBic = 0.8711*(distance**0.5301)*math.exp(-0.6316*distance)
    pEbic = 0.3845*(distance**0.5566)*math.exp(-0.2418*distance)
    pMotor = 0.03651*(distance**1.666)*math.exp(-0.1992*distance)
    pTaxi = 0.0341*(distance**0.6078)*math.exp(-0.02116*distance)
    pCar = 0.02486*(distance**0.8243)*math.exp(-0.0226*distance)
    pBus = 0.04737*(distance**0.6536)*math.exp(-0.0216*distance)
    pSub = 0.03616*(distance**0.8956)*math.exp(-0.02246*distance)
    return pCar, pBus, pSub

""" 4. 在某种模式下的出行距离分布概率 （08年交通运输工程信息学报 假设城市的出行距离服从瑞利分布） """
# def calDistProb(distance):
#     distance = distance/1000  # 单位换算 m -> km

#     cityBuiltArea = 823  # 城市建成区面积 （平方千米）
#     x1 = math.sqrt(cityBuiltArea)  # 系数1
#     # minCircleArea =     # 最小圆面积：以建成区为面积，以城市中心为原点的最小圆的面积 （平方千米）
#     # x2 = math.sqrt(minCircleArea/cityBuiltArea)  # 系数2：城市形状系数
#     x2 = 0.7
#     publicTransitProportion = ((32.8+33.6)/(30.4+60.8+9.6+32.8+33.6+21.6+19.2)+(44.8+64.0)/(8.8+33.6+10.4+44.8+64.0+33.6+32.8))  # 公交分担比率：取两种条件下的公交分担率的平均值
#     carProportion = ((21.6+19.2)/(30.4+60.8+9.6+32.8+33.6+21.6+19.2)+ \
#                          (33.6+32.8)/(8.8+33.6+10.4+44.8+64.0+33.6+32.8))/2  # 私家车分担率：取两种条件下的平均值
#     x3 = math.log10(publicTransitProportion/carProportion)  # 系数3：比值对数

#     y = -0.00116*x1+0.118*x2-0.02439*x3-0.01538   # 瑞利分布（或称二阶韦布尔分布）的lamda

#     return y*distance*math.exp(-0.5*y*(distance**2))   # 出行分布概率

""" 4. 在某种模式下的出行距离分布概率 （08年 nature Understanding individual human mobility patterns 假设出行服从被截断的幂律分布) """
def calDistProb(distance, beta = 1.75):
    distance = distance/1000   # 单位 m -->  km
    # beta = 1.75   # 20241024. 设置不同的 beta. 1.6, 1.75, 1.9
    r0 = 1.5     
    K = 80   # 单位 km
    return ((distance+r0)**(-1*beta))*math.exp(-1*distance/K)

""" 5.交通分布 输入网格的交通发生量和吸引量，返回各网格间的交通量"""
""" !!! 添加O的gridID，和D的gridId """
def trafficDistribution(GridsTripDf):  # 输入df(网格数量8748×属性11)，分别是gridID，经度，纬度，发生量r=0，吸引量r=0，发生量r=0.05，吸引量r=0.05，发生量r=0.1，吸引量r=0.1，发生量r=0.2，吸引量r=0.2，发生量r=0.4，吸引量r=0.4
    GridsTripDf.drop(columns=[
        # "production_r0","attraction_r0",
        "production_r005","attraction_r005",
        "production_r01","attraction_r01",
        "production_r015","attraction_r015",
        "production_r02","attraction_r02",
        "production_r025","attraction_r025",
        "production_r03","attraction_r03",
        "production_r035","attraction_r035",
        "production_r04","attraction_r04",
        "production_r045","attraction_r045",
        "production_r05","attraction_r05"],inplace=True)    # 只保留一项r值对应的交通发生量和吸引量
    
    gridsTripArray = GridsTripDf.values # 输入df(网格数量8748×属性5)，分别是gridID，经度，纬度，发生量，吸引量

    # 交通分布，数据维度(OD数量×属性6)，分别是OGridID,DGridID,car, bus, subway，distance(m)
    trafficDistributionArray = np.zeros((len(gridsTripArray)*len(gridsTripArray),6),dtype="int32")  
    m = 0  # 第m个OD
    for gridO in gridsTripArray:
        print("Begin to deal with origin (grid id: ", gridO[0], ")")
        x1, y1 = wgs84toWebMercator(gridO[1],gridO[2])  # 出发地质心坐标
        n = 0 # 第n个D
        # 目的地吸引度，数据维度(D数量×属性6)，分别是OGridID,DGridID,car, bus, subway，distance(m)
        gridDAttractionDegree = np.zeros((len(gridsTripArray),6),dtype="int32")
        # 遍历所有目的地，计算各个目的地的各模式的吸引度
        for gridD in gridsTripArray:  
            if gridO[0] == gridD[0]:
                continue
            else:
                x2,y2 = wgs84toWebMercator(gridD[1],gridD[2])  # 目的地质心坐标
                distance = calDistance(x1,y1,x2,y2)   # 距离，单位m
                P_distance = calDistProb(distance=distance, beta=1.6)   # 出行距离分布概率 TODO: change the beta
                carProb, busProb, subwayProb = calModalProb(distance)   # 模式选择概率

                # 出发网格ID
                gridDAttractionDegree[n][0] = gridO[0]
                # 目的网格ID
                gridDAttractionDegree[n][1] = gridD[0]
                # car的吸引度
                gridDAttractionDegree[n][2] = gridD[-1]*P_distance*carProb
                # bus的吸引度
                gridDAttractionDegree[n][3] = gridD[-1]*P_distance*busProb
                # subway的吸引度
                gridDAttractionDegree[n][4] = gridD[-1]*P_distance*subwayProb
                # 距离(m)
                gridDAttractionDegree[n][5] = distance

                # if gridO[0] == 2 and gridD[0] == 0:
                #     print(gridO)
                #     print(gridD)
                #     print(gridDAttractionDegree[n])
                #     print(P_distance)
                #     print(carProb)
                #     print(busProb)
                #     print(busProb)
                #     sys.exit(0)

            n += 1
        # 记录起终点网格ID
        trafficDistributionArray[m:(m+len(gridsTripArray)),0:2] = gridDAttractionDegree[:,0:2]   # 0:2表示从0(包括)到2(不包括)
        # 记录起终点网格的流量（各模式）
        if sum(sum(gridDAttractionDegree[:,2:5])) > 0:
            trafficDistributionArray[m:(m+len(gridsTripArray)),2:5] = gridO[-2]*gridDAttractionDegree[:,2:5]/(sum(sum(gridDAttractionDegree[:,2:5])))
        else:
            trafficDistributionArray[m:(m+len(gridsTripArray)),2:5] = 0

        # 记录起终点网格的距离
        trafficDistributionArray[m:(m+len(gridsTripArray)),5] = gridDAttractionDegree[:,5]
        m += len(gridsTripArray)  
    return trafficDistributionArray

if __name__ == "__main__":
    # 读取所有网格的交通发生量和吸引量  
    # 20221215 我不知道为什么，这里面的文件名字还是 0725的，明显不是最新版本的，最新版本的是 1009，最后一次修改“交通分布”结果是在 20220104
    # GridsTripDf = pd.read_csv(r"D:\【学术】\【研究生】\【方向】多模式交通网络鲁棒性\【数据】需求生成\【数据】南京市网格数据\网格交通发生量与吸引量_0724\网格交通发生量与吸引量_0724.csv")
    # 20241024 参数敏感性分析，生成不同的 beta 对应的
    GridsTripDf = pd.read_csv(r"X:\【学术】\【方向】多模式交通网络韧性\【数据】需求生成\【数据】南京市网格数据\网格交通发生量与吸引量_1009\网格交通发生量与吸引量_1009.csv")
    # 计算交通分布
    trafficDistributionArray = trafficDistribution(GridsTripDf)
    # 输出
    # 转化为dataframe
    df = pd.DataFrame(trafficDistributionArray,columns=["O","D","Car volume","Bus volume","Subway volume","Distance(m)"])
    invalid_index = df[(df["Car volume"] == 0) & (df["Bus volume"] == 0) & (df["Subway volume"] == 0)].index
    print("无效样本比例:", len(invalid_index) / len(df))
    df_cleaned = df.drop(index=invalid_index).reset_index(drop=True)
    # df.to_csv(r"D:\【学术】\【研究生】\【方向】多模式交通网络鲁棒性\【数据】需求生成\【数据】南京市网格数据\交通分布_0725\多模式交通分布_r0_0725.csv")
    # 20241024 参数敏感性分析，生成不同的 beta 对应的
    df_cleaned.to_csv(r"X:\【学术】\【方向】多模式交通网络韧性\【数据】需求生成\【数据】南京市网格数据\交通分布_1009\多模式交通分布_r0_1009(beta=1.6).csv")