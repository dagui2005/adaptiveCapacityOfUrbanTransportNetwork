package org.matsim.network;

/*
* @author: Chunhong Li'21.1109
* @Description: 理论上 network.xml 文件的坐标（web mercator）转换的是没有问题的，为何MATSim报错呢？
* 基于 MATSim 自身携带的转换方法，将 (web mercator) 坐标系下的坐标转换为 WGS84 的坐标；
* 检查MATSim转换与手动转换的结果是否相同。
* */

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

public class CoordSystemValid {
//    UTM 以WGS84坐标系为基准的投影坐标系，单位 m，存在适用范围
    public static String UTM50NAsEPSG = "EPSG:32650";
//  Web Mercator 投影坐标系，以 WGS84 坐标系为基准，单位 m，除极高纬度地区外，其余地区均适用
    public static String WebMercator = "EPSG:3857";

    public static void main(String[] args){
//        以下坐标来自  EPSG 官方转换网站。
//        以南京市市中心 新街口 经纬度为例. WGS84 经纬度坐标，单位：°.  EPSG:4326
        Coord coordWGS84 = new Coord(118.7786,32.04121);
//        同样是 南京市市中心 新街口  Web Mercator. 坐标：m.  EPSG:3857
        Coord coordWebMercator = new Coord(13222374.51,3769073.11);
//        同样是 南京市市中心 新街口 UTM Zone 50. 坐标：m. EPSG:32650
        Coord coordUtm50N = new Coord(667931.13,3546683.67);

//        WGS84 和 Web Mercator 的互换
        CoordinateTransformation wgs842WebMercator = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84,WebMercator);
        CoordinateTransformation webMercator2wgs84 = TransformationFactory.getCoordinateTransformation(WebMercator,TransformationFactory.WGS84);
        System.out.print("From WGS84, to Web Mercator: ");
        System.out.print(wgs842WebMercator.transform(coordWGS84));
        System.out.println(" ");
        System.out.print("From Web Mercator, to WGS84: ");
        System.out.print(webMercator2wgs84.transform(coordWebMercator));
        System.out.println(" ");

//        WGS84 和 Utm zone 50N 的互换
        CoordinateTransformation wgs842Utm50N = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84,UTM50NAsEPSG);
        CoordinateTransformation utm50N2wgs84 = TransformationFactory.getCoordinateTransformation(UTM50NAsEPSG,TransformationFactory.WGS84);
        System.out.print("From WGS84, to Utm50N: ");
        System.out.print(wgs842Utm50N.transform(coordWGS84));
        System.out.println(" ");
        System.out.print("From Utm50N, to WGS84: ");
        System.out.print(utm50N2wgs84.transform(coordUtm50N));
        System.out.println();

//        Web Mercator 和 Utm zone 50N 的互换
        CoordinateTransformation webMercator2Utm50N = TransformationFactory.getCoordinateTransformation(WebMercator,UTM50NAsEPSG);
        CoordinateTransformation utm50N2webMercator = TransformationFactory.getCoordinateTransformation(UTM50NAsEPSG,WebMercator);
        System.out.print("From Web Mercator, to Utm50N: ");
        System.out.print(webMercator2Utm50N.transform(coordWebMercator));
        System.out.println(" ");
        System.out.print("From Utm50N, to Web Mercator: ");
        System.out.print(utm50N2webMercator.transform(coordUtm50N));
    }

}
