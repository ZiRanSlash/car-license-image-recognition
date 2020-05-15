package com.yuxue.train;

import static org.bytedeco.javacpp.opencv_core.CV_32FC1;

import java.util.Vector;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.ANN_MLP;
import org.opencv.ml.Ml;
import org.opencv.ml.TrainData;

import com.yuxue.constant.Constant;
import com.yuxue.enumtype.Direction;
import com.yuxue.util.Convert;
import com.yuxue.util.FileUtil;


/**
 * 基于org.bytedeco.javacpp包实现的训练
 * 
 * 图片文字识别训练
 * 训练出来的库文件，用于判断切图是否包含车牌
 * 
 * 训练的svm.xml应用：
 * 1、替换res/model/svm.xml文件
 * 2、修改com.yuxue.easypr.core.PlateJudge.plateJudge(Mat) 方法
 *      将样本处理方法切换一下，即将对应被注释掉的模块代码取消注释
 * 
 * @author yuxue
 * @date 2020-05-14 22:16
 */
public class ANNTrain {

    private ANN_MLP ann = ANN_MLP.create();

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private final int numCharacter = Constant.strCharacters.length;

    // 默认的训练操作的根目录
    private static final String DEFAULT_PATH = "D:/PlateDetect/train/chars_recognise_ann/";

    // 训练模型文件保存位置
    private static final String MODEL_PATH = DEFAULT_PATH + "ann.xml";

    public static float[] projectedHistogram(final Mat img, Direction direction) {
        int sz = 0;
        switch (direction) {
        case HORIZONTAL:
            sz = img.rows();
            break;

        case VERTICAL:
            sz = img.cols();
            break;

        default:
            break;
        }

        // 统计这一行或一列中，非零元素的个数，并保存到nonZeroMat中
        float[] nonZeroMat = new float[sz];
        Core.extractChannel(img, img, 0);
        for (int j = 0; j < sz; j++) {
            Mat data = (direction == Direction.HORIZONTAL) ? img.row(j) : img.col(j);
            int count = Core.countNonZero(data);
            nonZeroMat[j] = count;
        }
        // Normalize histogram
        float max = 0;
        for (int j = 0; j < nonZeroMat.length; ++j) {
            max = Math.max(max, nonZeroMat[j]);
        }
        if (max > 0) {
            for (int j = 0; j < nonZeroMat.length; ++j) {
                nonZeroMat[j] /= max;
            }
        }
        return nonZeroMat;
    }


    public Mat features(Mat in, int sizeData) {

        float[] vhist = projectedHistogram(in, Direction.VERTICAL);
        float[] hhist = projectedHistogram(in, Direction.HORIZONTAL);

        Mat lowData = new Mat();
        if (sizeData > 0) {
            Imgproc.resize(in, lowData, new Size(sizeData, sizeData));
        }

        int numCols = vhist.length + hhist.length + lowData.cols() * lowData.rows();
        Mat out = new Mat(1, numCols, CvType.CV_32F);

        int j = 0;
        for (int i = 0; i < vhist.length; ++i, ++j) {
            out.put(0, j, vhist[i]);
        }
        for (int i = 0; i < hhist.length; ++i, ++j) {
            out.put(0, j, hhist[i]);
        }

        for (int x = 0; x < lowData.cols(); x++) {
            for (int y = 0; y < lowData.rows(); y++, ++j) {
                // float val = lowData.ptr(x, y).get(0) & 0xFF;
                double[] val = lowData.get(x, y);
                out.put(0, j, val[0]);
            }
        }
        return out;
    }

    public void train(int _predictsize, int _neurons) {
        Mat samples = new Mat(); // 使用push_back，行数列数不能赋初始值
        Vector<Integer> trainingLabels = new Vector<Integer>();
        // 加载数字及字母字符
        for (int i = 0; i < numCharacter; i++) {
            String str = DEFAULT_PATH + "learn/" + Constant.strCharacters[i];
            Vector<String> files = new Vector<String>();
            FileUtil.getFiles(str, files);

            int size = (int) files.size();
            for (int j = 0; j < size; j++) {
                Mat img = Imgcodecs.imread(files.get(j), 0);
                // System.err.println(files.get(j)); // 文件名不能包含中文
                Mat f = features(img, _predictsize);
                samples.push_back(f);
                trainingLabels.add(i); // 每一幅字符图片所对应的字符类别索引下标
            }
        }

        // 加载汉字字符
        for (int i = 0; i < Constant.strChinese.length; i++) {
            String str = DEFAULT_PATH + "learn/" + Constant.strChinese[i];
            Vector<String> files = new Vector<String>();
            FileUtil.getFiles(str, files);

            int size = (int) files.size();
            for (int j = 0; j < size; j++) {
                Mat img = Imgcodecs.imread(files.get(j), 0);
                // System.err.println(files.get(j));   // 文件名不能包含中文
                Mat f = features(img, _predictsize);
                samples.push_back(f);
                trainingLabels.add(i + numCharacter);
            }
        }


        //440   vhist.length + hhist.length + lowData.cols() * lowData.rows();
        // CV_32FC1 CV_32SC1 CV_32F
        Mat classes = new Mat(trainingLabels.size(), 65, CvType.CV_32F);
        
        float[] labels = new float[trainingLabels.size()];
        for (int i = 0; i < labels.length; ++i) {
            // labels[i] = trainingLabels.get(i).intValue();
            classes.put(i, trainingLabels.get(i), 1.f);
        }
        
        // classes.put(0, 0, labels);

        // samples.type() == CV_32F || samples.type() == CV_32S 
        TrainData train_data = TrainData.create(samples, Ml.ROW_SAMPLE, classes);

        ann.clear();
        Mat layers = new Mat(1, 3, CvType.CV_32F);
        layers.put(0, 0, samples.cols());
        layers.put(0, 1, _neurons);
        layers.put(0, 2, classes.cols());

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
        FileUtil.getFiles(DEFAULT_PATH + "test/", files);
        
        int i = 0;
        for (String string : files) {
            Mat img = Imgcodecs.imread(string, 0);
            Mat f = features(img, Constant.predictSize);
            
            Mat output = new Mat(1, 140, CvType.CV_32F);
            //ann.predict(f, output, 0);  // 预测结果
            System.err.println(string + "===>" + (int) ann.predict(f, output, 0));
            
            // ann.predict(f, output, 0);
            // System.err.println(string + "===>" + output.get(0, 0)[0]);
            
        }
        
    }

    public static void main(String[] args) {
        
        ANNTrain annT = new ANNTrain();
        // 这里演示只训练model文件夹下的ann.xml，此模型是一个predictSize=10,neurons=40的ANN模型
        // 可根据需要训练不同的predictSize或者neurons的ANN模型
        // 根据机器的不同，训练时间不一样，但一般需要10分钟左右，所以慢慢等一会吧。
        annT.train(Constant.predictSize, Constant.neurons);

        annT.predict();
        
        System.out.println("The end.");
    }


}