package com.yuxue.train;

import java.util.Random;
import java.util.Vector;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.ANN_MLP;
import org.opencv.ml.Ml;
import org.opencv.ml.TrainData;

import com.yuxue.constant.Constant;
import com.yuxue.util.FileUtil;
import com.yuxue.util.PlateUtil;


/**
 * 基于org.opencv包实现的训练
 * 
 * 图片文字识别训练
 * 训练出来的库文件，用于识别图片中的数字及字母
 * 
 * 测试了一段时间之后，发现把中文独立出来识别，准确率更高一点
 * 
 * 训练的ann.xml应用：
 * 1、替换res/model/ann.xml文件
 * 2、修改com.yuxue.easypr.core.CharsIdentify.charsIdentify(Mat, Boolean, Boolean)方法
 * 
 * @author yuxue
 * @date 2020-05-14 22:16
 */
public class ANNTrain {

    private ANN_MLP ann = ANN_MLP.create();

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    // 默认的训练操作的根目录
    private static final String DEFAULT_PATH = "D:/PlateDetect/train/chars_recognise_ann/";

    // 训练模型文件保存位置
    private static final String MODEL_PATH = DEFAULT_PATH + "ann.xml";


    /**
     * 进行膨胀操作
     * @param inMat
     * @return
     */
    public Mat dilate(Mat inMat) {
        Mat result = inMat.clone();
        Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.dilate(inMat, result, element);
        return result;
    }

    /**
     * 进行腐蚀操作
     * @param inMat
     * @return
     */
    public Mat erode(Mat inMat) {
        Mat result = inMat.clone();
        Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.erode(inMat, result, element);
        return result;
    }


    /**
     * 随机数平移
     * @param inMat
     * @return
     */
    public Mat randTranslate(Mat inMat) {
        Random rand = new Random();
        Mat result = inMat.clone();
        int ran_x = rand.nextInt(10000) % 5 - 2; // 控制在-2~3个像素范围内
        int ran_y = rand.nextInt(10000) % 5 - 2;
        return translateImg(result, ran_x, ran_y);
    }


    /**
     * 随机数旋转
     * @param inMat
     * @return
     */
    public Mat randRotate(Mat inMat) {
        Random rand = new Random();
        Mat result = inMat.clone();
        float angle = (float) (rand.nextInt(10000) % 15 - 7); // 旋转角度控制在-7~8°范围内
        return rotateImg(result, angle);
    }



    /**
     * 平移
     * @param img
     * @param offsetx
     * @param offsety
     * @return
     */
    public Mat translateImg(Mat img, int offsetx, int offsety){
        Mat dst = new Mat();
        //定义平移矩阵
        Mat trans_mat = Mat.zeros(2, 3, CvType.CV_32FC1);
        trans_mat.put(0, 0, 1);
        trans_mat.put(0, 2, offsetx);
        trans_mat.put(1, 1, 1);
        trans_mat.put(1, 2, offsety);
        Imgproc.warpAffine(img, dst, trans_mat, img.size());    // 仿射变换
        return dst;
    }


    /**
     * 旋转角度
     * @param source
     * @param angle
     * @return
     */
    public Mat rotateImg(Mat source, float angle){
        Point src_center = new Point(source.cols() / 2.0F, source.rows() / 2.0F);
        Mat rot_mat = Imgproc.getRotationMatrix2D(src_center, angle, 1);
        Mat dst = new Mat();
        // 仿射变换 可以考虑使用投影变换; 这里使用放射变换进行旋转，对于实际效果来说感觉意义不大，反而会干扰结果预测
        Imgproc.warpAffine(source, dst, rot_mat, source.size());    
        return dst;
    }


    public void train(int _predictsize, int _neurons) {
        Mat samples = new Mat(); // 使用push_back，行数列数不能赋初始值
        Vector<Integer> trainingLabels = new Vector<Integer>();
        Random rand = new Random();
        // 加载数字及字母字符
        for (int i = 0; i < Constant.numCharacter; i++) {
            String str = DEFAULT_PATH + "learn/" + Constant.strCharacters[i];
            Vector<String> files = new Vector<String>();
            FileUtil.getFiles(str, files);  // 文件名不能包含中文

            int count = 200; // 控制从训练样本中，抽取指定数量的样本
            for (int j = 0; j < count; j++) {

                Mat img = Imgcodecs.imread(files.get(rand.nextInt(files.size() - 1)), 0);
                Mat f = PlateUtil.features(img, _predictsize);
                samples.push_back(f);
                trainingLabels.add(i); // 每一幅字符图片所对应的字符类别索引下标

                // 增加随机平移样本
                samples.push_back(PlateUtil.features(randTranslate(img), _predictsize));
                trainingLabels.add(i); 

                // 增加随机旋转样本
                samples.push_back(PlateUtil.features(randRotate(img), _predictsize));
                trainingLabels.add(i); 

                // 增加膨胀样本
                /*samples.push_back(PlateUtil.features(dilate(img), _predictsize));
                trainingLabels.add(i);*/ 

                // 增加腐蚀样本
                /*samples.push_back(PlateUtil.features(erode(img), _predictsize));
                trainingLabels.add(i); */
            }
        }

        // 加载汉字字符
        for (int i = 0; i < Constant.strChinese.length; i++) {
            String str = DEFAULT_PATH + "learn/" + Constant.strChinese[i];
            Vector<String> files = new Vector<String>();
            FileUtil.getFiles(str, files);

            int count = 200; // 控制从训练样本中，抽取指定数量的样本
            for (int j = 0; j < count; j++) {
                Mat img = Imgcodecs.imread(files.get(rand.nextInt(files.size() - 1)), 0);
                Mat f = PlateUtil.features(img, _predictsize);
                samples.push_back(f);
                trainingLabels.add(i + Constant.numCharacter);

                // 增加随机平移样本
                samples.push_back(PlateUtil.features(randTranslate(img), _predictsize));
                trainingLabels.add(i + Constant.numCharacter);

                // 增加随机旋转样本
                samples.push_back(PlateUtil.features(randRotate(img), _predictsize));
                trainingLabels.add(i + Constant.numCharacter);

                // 增加膨胀样本
                /*samples.push_back(PlateUtil.features(dilate(img), _predictsize));
                trainingLabels.add(i + Constant.numCharacter);*/

                // 增加腐蚀样本
                samples.push_back(PlateUtil.features(erode(img), _predictsize));
                trainingLabels.add(i + Constant.numCharacter);
            }
        }

        samples.convertTo(samples, CvType.CV_32F);

        //440   vhist.length + hhist.length + lowData.cols() * lowData.rows();
        // CV_32FC1 CV_32SC1 CV_32F
        Mat classes = Mat.zeros(trainingLabels.size(), Constant.numAll, CvType.CV_32F);

        float[] labels = new float[trainingLabels.size()];
        for (int i = 0; i < labels.length; ++i) {
            classes.put(i, trainingLabels.get(i), 1.f);
        }

        // samples.type() == CV_32F || samples.type() == CV_32S 
        TrainData train_data = TrainData.create(samples, Ml.ROW_SAMPLE, classes);

        ann.clear();
        Mat layers = new Mat(1, 3, CvType.CV_32F);
        layers.put(0, 0, samples.cols());   // 样本特征数 140  10*10 + 20+20
        layers.put(0, 1, _neurons); // 神经元个数
        layers.put(0, 2, classes.cols());   // 字符数

        ann.setLayerSizes(layers);
        ann.setActivationFunction(ANN_MLP.SIGMOID_SYM, 1, 1);
        ann.setTrainMethod(ANN_MLP.BACKPROP);
        TermCriteria criteria = new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30000, 0.0001);
        ann.setTermCriteria(criteria);
        ann.setBackpropWeightScale(0.1);
        ann.setBackpropMomentumScale(0.1);
        ann.train(train_data);

        // FileStorage fsto = new FileStorage(MODEL_PATH, FileStorage.WRITE);
        // ann.write(fsto, "ann");
        ann.save(MODEL_PATH);
    }


    public void predict() {
        ann.clear();
        ann = ANN_MLP.load(MODEL_PATH);
        Vector<String> files = new Vector<String>();
        FileUtil.getFiles(DEFAULT_PATH + "test/", files);   // 获取测试文件

        String plate = "";
        for (String string : files) {
            Mat img = Imgcodecs.imread(string, 0);
            Mat f = PlateUtil.features(img, Constant.predictSize);

            int index = 0;
            double maxVal = -2;
            Mat output = new Mat(1, Constant.numAll, CvType.CV_32F);
            ann.predict(f, output);  // 预测结果
            for (int j = 0; j < Constant.numAll; j++) {
                double val = output.get(0, j)[0];
                if (val > maxVal) {
                    maxVal = val;
                    index = j;
                }
            }
            
            // 随机平移
            /*f = PlateUtil.features(randTranslate(img), Constant.predictSize);
            ann.predict(f, output);  // 预测结果
            for (int j = 0; j < Constant.numAll; j++) {
                double val = output.get(0, j)[0];
                if (val > maxVal) {
                    maxVal = val;
                    index = j;
                }
            }*/
            
            // 随机旋转
            /*f = PlateUtil.features(randRotate(img), Constant.predictSize);
            ann.predict(f, output);  // 预测结果
            for (int j = 0; j < Constant.numAll; j++) {
                double val = output.get(0, j)[0];
                if (val > maxVal) {
                    maxVal = val;
                    index = j;
                }
            }*/
            
            // 膨胀
            /*f = PlateUtil.features(dilate(img), Constant.predictSize);
            ann.predict(f, output);  // 预测结果
            for (int j = 0; j < Constant.numAll; j++) {
                double val = output.get(0, j)[0];
                if (val > maxVal) {
                    maxVal = val;
                    index = j;
                }
            }*/
            
            // 腐蚀  -- 识别中文字符效果会好一点，识别数字及字母效果会更差
            /*f = PlateUtil.features(erode(img), Constant.predictSize);
            ann.predict(f, output);  // 预测结果
            for (int j = 0; j < Constant.numAll; j++) {
                double val = output.get(0, j)[0];
                if (val > maxVal) {
                    maxVal = val;
                    index = j;
                }
            }*/

            if (index < Constant.numCharacter) {
                plate += String.valueOf(Constant.strCharacters[index]);
            } else {
                String s = Constant.strChinese[index - Constant.numCharacter];
                plate += Constant.KEY_CHINESE_MAP.get(s);
            }
        }
        System.err.println("===>" + plate);
        return;
    }

    public static void main(String[] args) {

        ANNTrain annT = new ANNTrain();
        // 这里演示只训练model文件夹下的ann.xml，此模型是一个predictSize=10,neurons=40的ANN模型
        // 可根据需要训练不同的predictSize或者neurons的ANN模型
        // 根据机器的不同，训练时间不一样，但一般需要10分钟左右，所以慢慢等一会吧
        // 可以考虑中文，数字字母分开训练跟识别，提高准确性
        annT.train(Constant.predictSize, Constant.neurons);

        annT.predict();

        System.out.println("The end.");
        return;
    }


}