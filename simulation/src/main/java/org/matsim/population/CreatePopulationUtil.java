/*
* @author: Chunhong Li'21.0925
* @Description: 通过时间离散化和空间离散化，将 zone2zone 的 travel demand
* 转化为 （符合MATSim要求的）agent2agent 的 travel demand；
* 其中，基于 （一天的）出发时间分布数据 将时间离散化（离散到每个小时上）；
*
* 基于 home-work(leisure, school等)-home的出行链生成日出行需求
*
* 空间离散化均处理到网格质心。
* */

package org.matsim.population;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Random;

public class CreatePopulationUtil {

//    zone2zone travel demand 文件路径；后续的变量及方法都定义为静态的，无需实例化即可使用； ！！！更改文件路径！！！
    private static String Z2ZDemandFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】需求生成\\【数据】南京市网格数据\\交通分布_1009\\多模式交通分布_r0_1009.csv";

//    agent2agent travel demand 文件路径；    ！！！更改文件路径！！！
    private static String A2ADemandFilePath = "src/main/resources/demand.noex20.100pct.xml";

//    提前定义 bufferedReader，避免程序都写入 try-catch语句中
    private static BufferedReader bufferedReader;

//    符合 MATSim 要求的需求数据
    private static Population population;

//    网格编号与质心坐标的对应字典。  已证明 “在主程序调用 ‘GetMercatorFromID’的静态方法，'gridCoord'也会随之变动”
    public static HashMap<String,Coord> gridCoord = GetMercatorFromID.gridCoord;

    /* 读取zone2zone的交通需求 */
    public static void readZ2ZDemand(String inputFilePath) throws FileNotFoundException {
//        文件列名依次为："O","D","Car volume","Bus volume","Subway volume","Distance(m)"
        InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(inputFilePath));
        bufferedReader = new BufferedReader(inputStreamReader);
    }

    /* 生成随机分布的时间，（所有时间）单位为 s。 （2021.10.12 该方法已舍弃，目前直接调用 GetRandomTime 方法）*/
    public static int getRandomTime(){
        Random random = new Random();  // 这里可以设置种子值，但设置种子值后，产生的随机数就是固定的了（无论重复多少次，结果都一样）
        return (int) (random.nextInt(17)+5)*3600;   // 依照均匀分布 随机生成 5点 到 22点的整点出发时刻
    }

    /* 生成一次 trip。 因此活动类型对研究结果无影响，所以都假设为 home --> work；输入的信息包括 Population, 起点网格编号， 终点网格编号， 交通方式， 个体编号 */
    public static void fillOnePlan(Population population,String oNum,String dNum,String mode,String order){
//        新增一个 agent。agent 编号命名格式 ：“起点网格的编号”+“X”+“终点网格的编号”+“X”+“编号”，如 1X23X33 表示从网格1到网格23的编号为33的 agent，只要OD相同，不管交通方式，都会在一起排序。
        Person person = population.getFactory().createPerson(Id.createPersonId(oNum+"X"+dNum+"X"+order));
//        新增一个 plan。
        Plan plan = population.getFactory().createPlan();

//        随机生成该主体出行的两个时间整点
        int[] randomHour = GetRandomTime.getTwoRandomTime();
        int[] randomMins = new int[]{GetRandomTime.getRandomMin(),GetRandomTime.getRandomMin()};

//        新增一个 home activity。 活动地点为网格质心
        Coord coord1 = gridCoord.get(oNum);
        Activity activity1 = population.getFactory().createActivityFromCoord("home",coord1);
        activity1.setEndTime(randomHour[0] * 3600 + randomMins[0] * 60);
        plan.addActivity(activity1);

//        新增一个 leg
        Leg leg1 = population.getFactory().createLeg(mode);
        plan.addLeg(leg1);

//        新增一个 activity (类型可以是 work, leisure, school, ……，这里暂且标为work)。 活动地点为网格质心
        Coord coord2 = gridCoord.get(dNum);
        Activity activity2 = population.getFactory().createActivityFromCoord("work",coord2);
        activity2.setEndTime(randomHour[1] * 3600 + randomMins[1] * 60);
        plan.addActivity(activity2);

//        新增一个 leg
        Leg leg2 = population.getFactory().createLeg(mode);
        plan.addLeg(leg2);

//        新增一个 activtiy （类型是home）。活动地点为网格质心
        Activity activity3 = population.getFactory().createActivityFromCoord("home",coord1);
        plan.addActivity(activity3);

        person.addPlan(plan);
        population.addPerson(person);
    }

    /* 循环填充 相同OD的主体的计划。 输入 car出行量，bus出行量，subway出行量，起点网格的编号，终点网格的编号，都是墨卡托投影坐标系*/
    public static void fillMultiplePlans(Population population, int carVolume,int busVolume, int subwayVolume,String oNum,String dNum){
        int order = 1;
        for(int i = 1; i <= carVolume; i++){
            fillOnePlan(population,oNum,dNum, TransportMode.car,String.valueOf(order));
            order++;
        }
        for(int i = 1; i <= busVolume; i++){
            fillOnePlan(population,oNum,dNum,TransportMode.pt,String.valueOf(order));
            order++;
        }
        for(int i = 1; i <= subwayVolume; i++){
            fillOnePlan(population,oNum,dNum,TransportMode.pt,String.valueOf(order));
            order++;
        }

    }


    public static void main(String[] args) throws IOException {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.loadScenario(config);
        population = scenario.getPopulation();

//        1. 填充网格编号与质心坐标的字典。
        new GetMercatorFromID();
//        2. 读取 zone2zone 交通需求数据
        readZ2ZDemand(Z2ZDemandFilePath);
//        3. 循环所有 OD 对，每对 OD 对都生成 0.01% 的出行需求
//        csv 首行："","O","D","Car volume","Bus volume","Subway volume","Distance(m)"
        System.out.println(bufferedReader.readLine());
        String line = null;
        double beta = 1;     //  样本仿真比例 1 ---> 100%
//        "/" in java 取整； so add "(double)" 以得到精确解
        String oNum;
        String dNum;
        int carVolume;
        int busVolume;
        int subwayVolume;
        int sumVolume = 0;
        int modifiedSumVolume = 0;
        while((line=bufferedReader.readLine())!=null){
            String[] item = line.split(",");
            oNum = item[1];     // 起点网格的编号  !!!一定要注意，第一列为无效列!!!
            dNum = item[2];     // 终点网格的编号

            carVolume = Integer.valueOf(item[3]);     // car 的交通量
            busVolume = Integer.valueOf(item[4]);     // bus 的交通量
            subwayVolume = Integer.valueOf(item[5]);   // subway 的交通量

//            calculate the sum of demand, generated by POIs.
            sumVolume += (carVolume + busVolume + subwayVolume) * 2;

            Double dis = Math.pow((gridCoord.get(oNum).getX() - gridCoord.get(dNum).getX()),2) + Math.pow((gridCoord.get(oNum).getY() - gridCoord.get(dNum).getY()),2);
            dis = Math.pow(dis,0.5);     // 20220330. 坐标已经转为 UTM-50N 投影坐标系

//            判断 OD 的距离，如果 OD 直线距离小于 1 km 大于 20km，则不添加
            if(dis<=1200 || dis >= 20000){
                continue;
            }

            carVolume = (int) Math.round(carVolume * beta);   // car 新的交通量
            busVolume = (int) Math.round(busVolume * beta);   // bus 新的交通量
            subwayVolume = (int) Math.round(subwayVolume * beta);   // subway 新的交通量

//            calculate the sum of modified demand.
            modifiedSumVolume += (carVolume + busVolume + subwayVolume) * 2;

            fillMultiplePlans(population,carVolume,busVolume,subwayVolume,oNum,dNum);
            System.out.println("······已生成OD对"+oNum+"-"+dNum+"的出行需求·······");
        }
//        写入需求文件
        new PopulationWriter(population).write(A2ADemandFilePath);
        System.out.println("······已生成符合MATSim格式的所有出行需求·······");
        System.out.println("······基于POI生成的总需求量：·······" + sumVolume);
        System.out.println(".......修正后的需求量：........" + modifiedSumVolume);
    }
}
