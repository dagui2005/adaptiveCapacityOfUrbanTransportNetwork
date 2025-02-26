package org.matsim.network.tif;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;

import java.util.Arrays;

/*
* Reference: https://blog.csdn.net/wzw114/article/details/120616448
* Key 1: 首先根据文件路径获取到对应的DataSet类，获取仿射矩阵，然后从DataSet类获取Band类；
* Key 2: 根据经纬度坐标确定所选像元（起始点）距离左上角（两个方向上）的距离；
* Key 3: 调用Band类的ReadRaster获取一个一维数组，这个一维数组就是该栅格数据的某一区域的值。
* Note: 20230202: ReadRaster 的输入参数需要确定起始点和原点在 x 方向和 y 方向上的距离，这个原点指的是 左上角的原点！
* 请记住，我们在这里面有两个系统，一个是经纬度系统，基准点（右上角）和被测点的经纬度都是已知的；
* 另一个系统是单位距离，一个栅格的长或宽表示一个距离，我们只用相对基准点（右上角）的距离，不用坐标！
* */
public class RasterDemo {

    public static void main(String[] args) {
        String filepath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】洪水数据\\Joint research centre data\\floodMapGL_rp100y\\floodMapGL_rp100y.tif";
        //注册
        gdal.AllRegister();
        //打开文件获取数据集
        Dataset dataset = gdal.Open(filepath,
                gdalconstConstants.GA_ReadOnly);
        if (dataset == null) {
            System.out.println("打开"+filepath+"失败"+gdal.GetLastErrorMsg());
            System.exit(1);
        }
        //获取驱动
        Driver driver = dataset.GetDriver();
        //获取驱动信息
        System.out.println("driver long name: " + driver.getLongName());
        int colCount = dataset.getRasterXSize();   // 栅格矩阵的列数
        int rowCount = dataset.getRasterYSize();   // 栅格矩阵的行数
        int bandCount = dataset.getRasterCount();    // 栅格矩阵的波段数
        System.out.println("RasterCount: " + bandCount);

        // 仿射矩阵，左上角像素的大地坐标和像素分辨率。
        // 共有六个参数，分表代表左上角x坐标；东西方向上图像的分辨率；如果北边朝上，地图的旋转角度，0表示图像的行与x轴平行；左上角y坐标；
        // 如果北边朝上，地图的旋转角度，0表示图像的列与y轴平行；南北方向上地图的分辨率。
        double[] gt = new double[6];
        dataset.GetGeoTransform(gt);
        System.out.println("仿射变换参数"+Arrays.toString(gt));

//        double[] values = new double[1];
//        Band band = dataset.GetRasterBand(1);
//        band.ReadRaster(0,3,1,1, values);
//        System.out.println(values[0]);

//        指定经纬度
//        double latitude  = 32.349094;
//        double longitude = 118.738713; // 栅格左上角的点
        double longitude = 88.358666; // Test point.
        double latitude  = 24.240507;

        int xOff = (int) ((longitude - gt[0]) / gt[1]);  // x pixel  点对应的栅格与左上角的栅格在x轴上相距的栅格数（因为左上角的栅格是(0,0)，所以也是栅格序号） = 东西方向（经度方向/X轴方向）的距离 / 该方向的图像分辨率
        int yOff = (int) ((latitude - gt[3]) / gt[5]); // y pixel  点对应的栅格与左上角的栅格在y轴上相距的栅格数（因为左上角的栅格是(0,0)，所以也是栅格序号） = 南北方向（纬度方向/y轴方向）的距离 / 该方向的图像分辨率
        System.out.println("Longitude: " + longitude + ", latitude: " + latitude);
        System.out.println("Distance in x: " + xOff + ", Distance in y: " + yOff);
        //遍历波段，获取该点对应的每个波段的值并打印到屏幕
        for (int i = 0; i < bandCount; i++){
            Band band = dataset.GetRasterBand(i+1);
            double[] values = new double[1];  // the value of selected pixels will be saved in "values".
            band.ReadRaster(xOff, yOff, 1, 1, values); // "xsize" and "ysize" determine the scope of selected area. So selected area has a pixel and "values" has only one value.
            System.out.format("Band"+(i+1)+": %s", values[0]);
        }

        //释放资源
        dataset.delete();

    }


}
