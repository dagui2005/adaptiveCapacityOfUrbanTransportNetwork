package org.matsim.network;

/*2021.3.13, Chunhong Li
* WGS84坐标系转换为墨卡托投影坐标系；
*
*/

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;

public class WGS2Mercator implements CoordinateTransformation{

    @Override
    public Coord transform(Coord coord){
        // 核心公式
        // 平面坐标x = 经度*20037508.34/108
        // 平面坐标y = log（tan（（90+纬度）*PI/360））/（PI/360）*20037508.34/180

        double x = coord.getX()*20037508.34/108;
        double y = Math.log(Math.tan((90+coord.getY())*Math.PI/360))/(Math.PI/180);
        y = y*20037508.34/180;

        return new Coord(x,y);
    }
}
