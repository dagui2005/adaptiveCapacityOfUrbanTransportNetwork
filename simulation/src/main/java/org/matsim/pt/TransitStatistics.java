package org.matsim.pt;

import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2022年04月07日 21:28
 * @Description: 统计公共交通线路数据。 主程序用于统计洪水破坏后的公共交通的运营线路数量。
 */
public class TransitStatistics {
    public static int[] statistics(TransitSchedule schedule) {
        /*
            Input: transit schedule
            Output: transit stop num after direct failures, transit stop num after indirect failures, transit line num, transit route num.
            20220410. DescriptiveScheduleStat.run() 也可以进行描述性统计
         */
//        统计 lines 数量
        int transitLineNum = schedule.getTransitLines().size();

//        统计 stops 数量
//        交通站点数量，直接被水淹没后剩余的站点数量，即运营失效中的直接失效。
        int transitStopNumAfterDirect = schedule.getFacilities().size();
//        交通站点数量，通过公共交通线路计算. 这样计算的是间接失效后的有效站点数量，包括站点虽然没有被淹没但是其上面已经没有公共交通线路了，所以失效了。
        int transitStopNumAfterIndirect = 0;
//        但直接加和所有线路的站点的结果会不太对，因为可能有一些站点经过很多条公交线路，这些站点会被重复计算，因此要排除重复计算的站点数。
        Set<TransitStopFacility> transitStopFacilities = new HashSet<TransitStopFacility>();
        for (TransitLine transitLine : schedule.getTransitLines().values()
        ) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()
            ) {
                for (TransitRouteStop transitRouteStop : transitRoute.getStops()
                ) {
                    if (!transitStopFacilities.contains(transitRouteStop.getStopFacility())) {
//                    若这个站点没有被统计过，才进行统计
                        transitStopNumAfterIndirect += 1;
                        transitStopFacilities.add(transitRouteStop.getStopFacility());
                    }
                }
            }
        }

//        统计 routes 数量
        int transitRouteNum = 0;
        for (TransitLine transitLine : schedule.getTransitLines().values()
        ) {
            transitRouteNum += transitLine.getRoutes().size();
        }

//        int[] output = new int[3];
//        output[0] = transitStopNum;
//        output[1] = transitLineNum;
//        output[2] = transitRouteNum;
//        return output;

        return new int[]{transitStopNumAfterDirect, transitStopNumAfterIndirect, transitLineNum, transitRouteNum};
    }

    public static void main(String[] args) {
        /* 4. 输出 */
        //第一步：设置输出的文件路径
        //如果该目录下不存在该文件，则文件会被创建到指定目录下。如果该目录有同名文件，那么该文件将被覆盖。
        File writeFile = new File("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【分析】影响评估\\洪水场景-双层多模式网络\\【分析】运营破坏相关分析\\运营破坏统计.csv");

        try {
            //第二步：通过BufferedReader类创建一个使用默认大小输出缓冲区的缓冲字符输出流
            BufferedWriter writeText = new BufferedWriter(new FileWriter(writeFile));

            // 首行
            writeText.write("runoff," + "station_num_of_coupled_transit_after_direct," + "station_num_of_coupled_transit_after_indirect," + "lines_num_of_coupled_transit," + "routes_num_of_coupled_transit,"
                    + "station_num_of_bus_after_direct," + "station_num_of_bus_after_indirect," + "lines_num_of_bus," + "routes_num_of_bus,"
                    + "station_num_of_metro_after_direct," + "station_num_of_metro_after_indirect," + "lines_num_of_metro," + "routes_num_of_metro"
            );

            //第三步：将文档的下一行数据赋值给lineData，并判断是否为空，若不为空则输出
            int runoff = 0;
            while (runoff <= 290) {
                /* 1, 输入 schedule 文件 */
                //  被洪水破坏后的 MATSim schedule XML 文件
                String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】线网数据\\after floods damages\\XML\\" + "ChinaNormalAflddph" + runoff + ".residual transit schedule.xml";
                TransitSchedule coupledSchedule = ScheduleTools.readTransitSchedule(schedulePath);

                /* 2. 将耦合的 schedule 拆成 busSchedule, metroSchedule */
                List<TransitSchedule> transitSchedules = DecoupleBusMetro.decouple(coupledSchedule);
                TransitSchedule busSchedule = transitSchedules.get(0);
                TransitSchedule metroSchedule = transitSchedules.get(1);

                /* 3. 统计 lines number, route number, stop number */
                int[] coupledInfo = statistics(coupledSchedule);
                int[] busInfo = statistics(busSchedule);
                int[] metroInfo = statistics(metroSchedule);

                writeText.newLine();    //换行
                //调用write的方法将字符串写到流中
                writeText.write(runoff + "," + coupledInfo[0] + "," + coupledInfo[1] + "," + coupledInfo[2] + "," + coupledInfo[3] + "," +
                        busInfo[0] + "," + busInfo[1] + "," + busInfo[2] + "," + busInfo[3] + "," +
                        metroInfo[0] + "," + metroInfo[1] + "," + metroInfo[2] + "," + metroInfo[3]);

                if (runoff < 10) {
                    runoff += 1;
                } else {
                    runoff += 5;
                }
            }

            //使用缓冲区的刷新方法将数据刷到目的地中
            writeText.flush();
            //关闭缓冲区，缓冲区没有调用系统底层资源，真正调用底层资源的是FileWriter对象，缓冲区仅仅是一个提高效率的作用
            //因此，此处的close()方法关闭的是被缓存的流对象
            writeText.close();
        } catch (FileNotFoundException e) {
            System.out.println("没有找到指定文件");
            System.out.println(e);
        } catch (IOException e) {
            System.out.println("文件读写出错");
        }

    }

}
