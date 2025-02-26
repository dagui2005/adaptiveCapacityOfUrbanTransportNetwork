package org.matsim.pt;

import org.matsim.pt2matsim.run.gis.Schedule2Geojson;

/**
 * @author: Chunhong li
 * @date: 2022年04月10日 9:32
 * @Description: 调用 pt2matsim.
 */
public class MySchedule2Shp {
    public static void main(String[] args) {
        String crs = "EPSG:32650";
        int seed = 7;
        while (seed <= 9) {
//        way 1. pt2matsim
            double threshold = 0.0;
            while (threshold <= 15) {
                String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after random damages\\xml\\" + "residual schedule.randomDamages.thereshold" + threshold + "m.seed" + seed + ".xml";
                String shpFolder = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after random damages\\geojson\\" + "residual schedule.randomDamages.thereshold" + threshold + "m.seed" + seed + ".geojson";
                Schedule2Geojson.main(new String[]{crs, shpFolder, schedulePath});

                threshold += 0.5;
            }
            seed += 1;
        }
    }
//        way 2
//        Schedule2ShapeFile.main();
}
