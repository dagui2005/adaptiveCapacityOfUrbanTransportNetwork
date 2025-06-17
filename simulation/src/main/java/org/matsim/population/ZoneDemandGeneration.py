""" 
@author: Chunhong Li'21.0630 revised by Li'1006
@Description: 基于重力模型生成 网格的交通发生量和吸引量；已有数据：每个grid的WGS84经纬度，POI数，人口，及GDP
"""

import math
import numpy as np
import pandas as pd
import random

""" 1.WGS84坐标系转换为web墨卡托投影 """
""" web墨卡托即将椭球视为圆球 """
def wgs84toWebMercator(lon,lat):
    x =  lon*20037508.342789/180
    y =math.log(math.tan((90+lat)*math.pi/360))/(math.pi/180)
    y = y *20037508.34789/180
    return x,y 

""" 2.导入所有POI数据 """
""" 共有POI 182660个; POI编号,网格编号,网格重要度,网格质心投影x,投影y;POI小类(汉字,补充在列表中) """
def importPOI():
    POINum = 182660
    POIInfo = np.zeros(shape=(POINum,14)) # POI信息 (X 个POI, POI各种信息见下)
    POIInfo_S =  []  # POI小类，汉字
    POIInputPath = r"D:\【学术】\【研究生】\【方向】多模式交通网络鲁棒性\【数据】需求生成\【数据】POI\POI对应网格经纬度重要度等_0925.txt"
    with open(POIInputPath,encoding='utf-8') as POIInput:
        firstLine = POIInput.readline()
        m = 0
        while m<=(POINum-1):  # 对每个POI
            everyPOIInfoList = POIInput.readline().strip("\n").split(',')
            POIInfo[m,0] = int(everyPOIInfoList[2])  # POI编号 从0计
            POIInfo_S.append(everyPOIInfoList[6])  # POI小类
            
            POIInfo[m,1] = int(everyPOIInfoList[-13])  # 网格编号   
            
            # 网格质心投影x; 网格质心投影y
            try:
                POIInfo[m,2],POIInfo[m,3] = wgs84toWebMercator(float(everyPOIInfoList[-13]),float(everyPOIInfoList[-12]))
                # 建议使用负数索引，避免错误
            except:
                print("错误出现")
                print(everyPOIInfoList[6])     
                print(m)    
            # 不用写入 r = 0 时的情况，因为那时，所有网格（POI）重要值都为1.
            # POIInfo[m,4] = float(everyPOIInfoList[-10])  # 网格重要度 r = 0.05
            # POIInfo[m,5] = float(everyPOIInfoList[-9])  # 网格重要度 r = 0.1
            # POIInfo[m,6] = float(everyPOIInfoList[-8])  # 网格重要度 r = 0.15
            # POIInfo[m,7] = float(everyPOIInfoList[-7])  # 网格重要度 r = 0.2
            # POIInfo[m,8] = float(everyPOIInfoList[-6])  # 网格重要度 r = 0.25
            # POIInfo[m,9] = float(everyPOIInfoList[-5])  # 网格重要度 r = 0.3
            # POIInfo[m,10] = float(everyPOIInfoList[-4])  # 网格重要度 r = 0.35
            # POIInfo[m,11] = float(everyPOIInfoList[-3])  # 网格重要度 r = 0.4
            # POIInfo[m,12] = float(everyPOIInfoList[-2])  # 网格重要度 r = 0.45
            # POIInfo[m,13] = float(everyPOIInfoList[-1])  # 网格重要度 r = 0.5
                    
            m += 1
    return POIInfo,POIInfo_S

""" 3.导入所有的POI-trip生成率 """
def importPOITripRate():
    POITripRatePath = r"D:\【学术】\【研究生】\【方向】多模式交通网络鲁棒性\【数据】需求生成\【数据】POI_Trip生成率\GaodePOITripRate_1008.csv"
    POITripRate = {} # POI的交通生成率
    POITripRateDf = pd.read_csv(POITripRatePath,encoding='gbk')
    for i in range(len(POITripRateDf)):
        POITripRate[POITripRateDf['小类'].iloc[i]] = [int(POITripRateDf['BJ_production'].iloc[i]),int(POITripRateDf['BJ_attraction'].iloc[i])]
    return POITripRate

""" 4.计算所有（有POI的）有效格子的交通发生量和吸引量 """
def tripGeneration(POIInfo,POIInfo_S,POITripRate):
    # 计算有效网格数量
    # gridsNum = len(set(POIInfo[:,1]))  
    # print("共有%d个有效网格"%(gridsNum))
    # 导入网格文件，只为了计算网格数
    gridsDf = pd.read_csv(r"D:\【学术】\【研究生】\【方向】多模式交通网络鲁棒性\【数据】需求生成\【数据】南京市网格数据\汇总数据_0629\表转excel\南京市网格数据_归一化重要度_0924.csv")
    print("共有%d个网格"%(len(gridsDf)))
    gridTripGeneration = np.zeros((len(gridsDf),3))  # 存储每个网格的网格编号，交通发生量和吸引量 (r = 0,0.05,……0.5)
    k = 0   # 计数君，第几个POI
    invalidPOITypes = set()   # 无POI-trip-rate的POI type集合
    for POI in POIInfo:  # 对每个POI计算交通发生量和吸引量，并统计到对应网格内
        POIType = POIInfo_S[k]

        try:
            # 判断该POI是否拥有多个属性，若有多个属性则选择级别最低的最小类（经手动查询一般是列表的第一个）
            if "|" not in POIType:
                production = POITripRate[POIType][0] 
                arraction = POITripRate[POIType][1]
            else:
                POITypes = POIType.split("|")  # 多个POI属性列表
                validPOITypes = [i for i in POITypes if i in POITripRate] # 有效的POI属性列表
                production = POITripRate[validPOITypes[0]][0] # 选择第一个，即最为细分的类别
                arraction = POITripRate[validPOITypes[0]][1]
        except:
            production,arraction = 0,0
            invalidPOITypes.add(POIInfo_S[k]) 
        # gridTripGeneration[int(POI[1])][0] = int(POI[1])  # 网格编号 注释的原因在于有些网格并没有POI，而此行代码无法更新这些网格的编号，不如最后直接一次性生成
        gridTripGeneration[int(POI[1])][1] += production  # r = 0 交通发生量
        gridTripGeneration[int(POI[1])][2] += production  
        # gridTripGeneration[int(POI[1])][3] += production*POI[4]  # r = 0.05 交通发生量
        # gridTripGeneration[int(POI[1])][4] += production*POI[4]  
        # gridTripGeneration[int(POI[1])][5] += production*POI[5]  # r = 0.1 交通发生量
        # gridTripGeneration[int(POI[1])][6] += production*POI[5]  
        # gridTripGeneration[int(POI[1])][7] += production*POI[6]  # r = 0.15 交通发生量
        # gridTripGeneration[int(POI[1])][8] += production*POI[6]  
        # gridTripGeneration[int(POI[1])][9] += production*POI[7]  # r = 0.2 交通发生量
        # gridTripGeneration[int(POI[1])][10] += production*POI[7]  
        # gridTripGeneration[int(POI[1])][11] += arraction*POI[8]   # r = 0.25 交通发生量
        # gridTripGeneration[int(POI[1])][12] += production*POI[8]  
        # gridTripGeneration[int(POI[1])][13] += arraction*POI[9]   # r = 0.3 交通发生量
        # gridTripGeneration[int(POI[1])][14] += production*POI[9]  
        # gridTripGeneration[int(POI[1])][15] += arraction*POI[10]   # r = 0.35 交通发生量
        # gridTripGeneration[int(POI[1])][16] += production*POI[10]  
        # gridTripGeneration[int(POI[1])][17] += arraction*POI[11]   # r = 0.4 交通发生量
        # gridTripGeneration[int(POI[1])][18] += arraction*POI[11]   
        # gridTripGeneration[int(POI[1])][19] += arraction*POI[12]   # r = 0.45 交通发生量
        # gridTripGeneration[int(POI[1])][20] += arraction*POI[12]   
        # gridTripGeneration[int(POI[1])][21] += arraction*POI[13]   # r = 0.5 交通发生量
        # gridTripGeneration[int(POI[1])][22] += arraction*POI[13]   

        k+=1

    # 一次性更新 gridTripGeneration 的全部网格编号
    gridTripGeneration[:,0] = np.arange(0,len(gridTripGeneration),1,dtype=np.int16) # 前包后不包
    return gridTripGeneration,invalidPOITypes

""" 5.导出文件：网格的交通发生量和吸引量 """
def exportGridsTrip(gridTripGenerition):
    outputPath = r"D:\【学术】\【研究生】\【方向】多模式交通网络鲁棒性\【数据】需求生成\【数据】南京市网格数据\网格交通发生量与吸引量_1009\网格交通发生量与吸引量_1009.csv"
    GridsTripDf = pd.DataFrame(gridTripGenerition,columns=[
        "gridId","production_r0","attraction_r0",
        # "production_r005","attraction_r005",
        # "production_r01","attraction_r01",
        # "production_r015","attraction_r015",
        # "production_r02","attraction_r02",
        # "production_r025","attraction_r025",
        # "production_r03","attraction_r03",
        # "production_r035","attraction_r035",
        # "production_r04","attraction_r04",
        # "production_r045","attraction_r045",
        # "production_r05","attraction_r05"
    ])
    GridsTripDf.to_csv(outputPath)

if __name__ == "__main__":
    # 导入POIs
    POIInfo,POIInfo_S = importPOI() 
    # 导入POI_TripRate
    POITripRate = importPOITripRate()
    # 生成所有格子的交通发生量和吸引量
    gridTripGeneration,invalidPOITypes = tripGeneration(POIInfo,POIInfo_S,POITripRate)
    # 导出文件  !!!注意，生成的文件的gridId需要手动调整，从0开始递加即可，+1; 另外需手动添加经纬度信息!!!!
    exportGridsTrip(gridTripGeneration)

    print("`````````````````")
    print("无效的POI类型共%d个"%(len(invalidPOITypes)))
    print(invalidPOITypes)
