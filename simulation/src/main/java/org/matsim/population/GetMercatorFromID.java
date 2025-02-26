/*
* @author: Chunhong Li
* @Description:本程序用于转换 网格ID 到 （其网格质心的）墨卡托投影坐标
* */

package org.matsim.population;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.*;
import java.util.HashMap;

public class GetMercatorFromID {
//    网格信息 csv 文件路径
//    private static String gridInfoPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】需求生成\\【数据】南京市网格数据\\网格交通发生量与吸引量_0925\\网格交通发生量与吸引量_0925.csv";
    // 20241025 sensitivity analysis
    private static String gridInfoPath = "X:\\【学术】\\【方向】多模式交通网络韧性\\【数据】需求生成\\【数据】南京市网格数据\\网格交通发生量与吸引量_1009\\网格交通发生量与吸引量_1009.csv";

//    读取 网格ID 与 质心坐标的对应关系，csv格式
    private static BufferedReader bufferedReader;

//    本程序最重要的字典，gridID --- UTM-50N
    public static HashMap<String,Coord> gridCoord = new HashMap<String, Coord>();

    //    UTM 以WGS84坐标系为基准的投影坐标系，单位 m，存在适用范围
    public static String UTM50NAsEPSG = "EPSG:32650";
    //  Web Mercator 投影坐标系，以 WGS84 坐标系为基准，单位 m，除极高纬度地区外，其余地区均适用
//    public static String WebMercator = "EPSG:3857";

    /* 1. 读取文件   */
    private static void readGridInfo() throws FileNotFoundException{
        InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(gridInfoPath));
        bufferedReader = new BufferedReader(inputStreamReader);
    }

    /* 2. 填充字典信息 */
    private static void fillGridCoord() throws IOException {
//        csv的第一行
        System.out.println("标题行");
        System.out.println(bufferedReader.readLine());
//        循环读取 csv 信息
        String line = null;
        while ((line=bufferedReader.readLine())!=null){
//            很巧妙地 在判断条件时，已读取并保存csv这一行的信息，后续不需要再读取
            String item[] = line.split(",");
            String gridID = item[0];   // 网格ID，字符串形式
            Coord coord = new Coord(Double.valueOf(item[1]),Double.valueOf(item[2])); // WGS84坐标
//            向字典添加元素
            gridCoord.put(gridID,coord);
        }
    }

    /*  3. 将字典中每个网格质心的坐标 由 WGS84 坐标系转换为 UTM-50N */
    private static void transformCoord(){
        CoordinateTransformation wgs842Utm50N = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84,UTM50NAsEPSG);
        for(String gridID:gridCoord.keySet()){
            Coord utmZ50NCoord = wgs842Utm50N.transform(gridCoord.get(gridID));
//            覆盖原来的键值对
            gridCoord.put(gridID,utmZ50NCoord);
        }
    }

    /* 4. 把前三个合并*/
    public GetMercatorFromID() throws IOException {
        readGridInfo();
        fillGridCoord();
        transformCoord();
    }


    public static void main(String[] args) throws IOException {
//        just a demo; 经验证，该程序有效
        readGridInfo();
        fillGridCoord();
        transformCoord();
        CoordinateTransformation utmZone50N2wgs84 = TransformationFactory.getCoordinateTransformation(UTM50NAsEPSG,TransformationFactory.WGS84);
        for(String gridID:gridCoord.keySet()){
            System.out.printf("grid ID: %s, UTM Zone 50N coord, x = %f, y = %f.%n",gridID,gridCoord.get(gridID).getX(),gridCoord.get(gridID).getY());
            System.out.printf("grid ID: %s, WGS 84 coord, x = %f, y = %f.%n",gridID,
                    utmZone50N2wgs84.transform(gridCoord.get(gridID)).getX(),
                    utmZone50N2wgs84.transform(gridCoord.get(gridID)).getY());
            System.out.println();
        }
    }

}
