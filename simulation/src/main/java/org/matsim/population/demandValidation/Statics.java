/*
@author: Chunhong Li'21.1004
@Description: 本程序用于统计 需求数量.将zone2zone的各个OD对各个交通方式的交通需求进行累加即可
* */


package org.matsim.population.demandValidation;

import java.io.*;

public class Statics {

//    生成的 csv 格式的需求文件 （以网格形式）. “”  "o" "d"  "car volume" "bus volume" "subway volume" "distance"
    private static String Z2ZDemandFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】需求生成\\【数据】南京市网格数据\\交通分布_1009\\多模式交通分布_r0_1009.csv";

//    提前定义 bufferedReader，避免程序都写入 try-catch语句中
    private static BufferedReader bufferedReader;

    /* 读取zone2zone的交通需求 */
    public static void readZ2ZDemand(String inputFilePath) throws FileNotFoundException {
//        文件列名依次为：",","O","D","Car volume","Bus volume","Subway volume","Distance(m)"
        InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(inputFilePath));
        bufferedReader = new BufferedReader(inputStreamReader);
    }

    public static void main(String[] args) throws IOException {
//        2. 统计 生成的全部的需求数量 （理论上是一个小时的）
        readZ2ZDemand(Z2ZDemandFilePath);
        System.out.println(bufferedReader.readLine());   // csv的首行
        String line=null;
        int tripNum = 0;   // 全部的需求总数
        int busTripNum = 0;    // bus 需求数
        int subwayTripNum = 0;   //subway 需求数
        int carTripNum = 0;   //car 需求数
        while((line=bufferedReader.readLine())!=null){
            String[] item = line.split(",");
            tripNum += Integer.valueOf(item[3])+Integer.valueOf(item[4])+Integer.valueOf(item[5]);
            busTripNum += Integer.valueOf(item[4]);
            subwayTripNum += Integer.valueOf(item[5]);
            carTripNum += Integer.valueOf(item[3]);
        }

        System.out.println("````````````````````````");
        System.out.println("bus出行人次数/h");
        System.out.println(busTripNum);
        System.out.println("subway出行人次数/h");
        System.out.println(subwayTripNum);
        System.out.println("car出行人次数/h");
        System.out.println(carTripNum);
        System.out.println("出行总人次数/h");
        System.out.println(tripNum);

    }
}
