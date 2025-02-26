/*
* @author: Chunhong LI'21.1012
* @Description: 本程序为 CreatePopulationUtil 的辅助程序，用于生成 agent 的出发时刻。
* 出发时间依据 北京交通发展年度报告2020；时间间隔为 1h；
* 假设居民均整点出发，从早上5点到晚上21点；Jau.2022 整点出发改为整分钟出发，即在原来程序的基础上，均匀离散整点出发时间。
* */

package org.matsim.population;

import java.util.Random;

public class GetRandomTime {
//    居民随时间的累计出发概率数组；时间从早上 5 点到晚上 21 点；一小时为一个单位；取car, bus, subway 的平均值
    private static double[] departureTD = new double[]{0.001,0.026,0.156,0.406,0.466,0.496,0.526,0.546,0.571,0.596,0.626,0.661,0.761,0.931,0.966,0.986,1};

//    依据出发时间累计分布概率，生成出发时间。输出整点出发时间（h）
    public static int getOneRandomTime(){
//        首先生成一个 0-1 的均匀随机数
        Random r = new Random();
        double x0 = r.nextDouble();
//        计算 |所有的出发时刻的概率-x0|
        double[] departureTD_x0 = new double[departureTD.length];
        for(int i=0;i<departureTD.length;i++){
            departureTD_x0[i] = Math.abs(departureTD[i]-x0);
        }
//        取 集合|所有的出发时刻的概率-x0| 的最小值，以及最小值对应的索引
        int index = 0;
        for(int i=0;i<departureTD_x0.length;i++){
            if(departureTD_x0[i]<departureTD_x0[index]){
                index=i;
            }
        }
//        出发时刻 = 最小值对应的索引 + 5 （系统从5点开始）
        return index+5;

    }

//    生成一个主体的一天的两次出发时间，两次出发时间间隔至少4小时
    public static int[] getTwoRandomTime(){
//        初始化 包含两个出发时刻 的数组
        int[] departureTimes = new int[2];

//        初始化出发时刻
        int time1 = getOneRandomTime();
        int time2 = getOneRandomTime();
        while (Math.abs(time1-time2)<4){
//            若两个时间差不足4，则一直生成直到满足条件
            time1 = getOneRandomTime();
            time2 = getOneRandomTime();
        }
        if(time1<time2){
            departureTimes[0] = time1;
            departureTimes[1] = time2;
        }
        else {
            departureTimes[0] = time2;
            departureTimes[1] = time1;
        }

        return departureTimes;
    }

    /* 均匀离散时间。 @Chunhong Li, Jau.2022 */
    public static int getRandomMin(){
//        首先生成一个 0-1 的均匀随机数
        Random r = new Random();
        double x0 = r.nextDouble();
        return (int) (x0 * 60);
    }

//    检验代码的可行性，经检验，可行
    public static void main(String[] args){
        for(int i:getTwoRandomTime()){
            System.out.println(i);
        }

        System.out.println(getRandomMin());
    }

}
