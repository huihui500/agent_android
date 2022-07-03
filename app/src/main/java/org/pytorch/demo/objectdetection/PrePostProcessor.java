// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package org.pytorch.demo.objectdetection;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

// 任务结果保存
class Mission{
    // 0 交通标志; 1 车牌; 2 图形； 3 qrcode
    int mission_index;

    ArrayList<Integer> traffic_sign;
    // 车牌: 车牌字符，车牌颜色;
    // qrcode: 二维码内容与颜色;
    ArrayList<String> cq;
    ArrayList<Integer> cq_color;

    ArrayList<Integer> graph;
    ArrayList<Integer> graph_color;

};

class Result {
    int classIndex;
    Float score;
    Rect rect;
    Rect raw_rect;

    public Result(int cls, Float output, Rect rect, Rect raw_rect) {
        this.classIndex = cls;
        this.score = output;
        this.rect = rect;
        this.raw_rect = raw_rect;
    }
};

public class PrePostProcessor {
    // for yolov5 model, no need to apply MEAN and STD
    static float[] NO_MEAN_RGB = new float[] {0.0f, 0.0f, 0.0f};
    static float[] NO_STD_RGB = new float[] {1.0f, 1.0f, 1.0f};

    // TODO:model input image size
    static int mInputWidth = 320;
    static int mInputHeight = 320;

    // model output is of size 25200*(num_of_class+5)
    private static int mOutputRow = 6300; // as decided by the YOLOv5 model for input image of size 640*640
    private static int mOutputColumn = 15; // left, top, right, bottom, score and 80 class probability
    private static float mThreshold = 0.25f; // score above which a detection is generated
    private static int mNmsLimit = 15;
    private static float iouThreshold = 0.45f;

    static String[] mClasses;
//    static String[] graphClasses;
    private static int mOutputColumn_graph = 10; // left, top, right, bottom, score and 80 class probability


    // The two methods nonMaxSuppression and IOU below are ported from https://github.com/hollance/YOLO-CoreML-MPSNNGraph/blob/master/Common/Helpers.swift
    /**
     Removes bounding boxes that overlap too much with other boxes that have
     a higher score.
     - Parameters:
     - boxes: an array of bounding boxes and their scores
     - limit: the maximum number of boxes that will be selected
     - threshold: used to decide whether boxes overlap too much
     */
    static ArrayList<Result> nonMaxSuppression(ArrayList<Result> boxes, int limit, float threshold) {

        // Do an argsort on the confidence scores, from high to low.
        Collections.sort(boxes,
                new Comparator<Result>() {
                    @Override
                    public int compare(Result o1, Result o2) {
                        return o2.score.compareTo(o1.score);
                    }
                });

        ArrayList<Result> selected = new ArrayList<>();
        boolean[] active = new boolean[boxes.size()];
        Arrays.fill(active, true);
        int numActive = active.length;

        // The algorithm is simple: Start with the box that has the highest score.
        // Remove any remaining boxes that overlap it more than the given threshold
        // amount. If there are any boxes left (i.e. these did not overlap with any
        // previous boxes), then repeat this procedure, until no more boxes remain
        // or the limit has been reached.
        boolean done = false;
        for (int i=0; i<boxes.size() && !done; i++) {
            if (active[i]) {
                Result boxA = boxes.get(i);
                selected.add(boxA);
                if (selected.size() >= limit) break;

                for (int j=i+1; j<boxes.size(); j++) {
                    if (active[j]) {
                        Result boxB = boxes.get(j);
                        if (IOU(boxA.rect, boxB.rect) > threshold) {
                            active[j] = false;
                            numActive -= 1;
                            if (numActive <= 0) {
                                done = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return selected;
    }

    /**
     Computes intersection-over-union overlap between two bounding boxes.
     */
    static float IOU(Rect a, Rect b) {
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        if (areaA <= 0.0) return 0.0f;

        float areaB = (b.right - b.left) * (b.bottom - b.top);
        if (areaB <= 0.0) return 0.0f;

        float intersectionMinX = Math.max(a.left, b.left);
        float intersectionMinY = Math.max(a.top, b.top);
        float intersectionMaxX = Math.min(a.right, b.right);
        float intersectionMaxY = Math.min(a.bottom, b.bottom);
        float intersectionArea = Math.max(intersectionMaxY - intersectionMinY, 0) *
                Math.max(intersectionMaxX - intersectionMinX, 0);
        return intersectionArea / (areaA + areaB - intersectionArea);
    }

    static ArrayList<Result> outputsToNMSPredictions(float[] outputs, float imgScaleX, float imgScaleY, float ivScaleX, float ivScaleY, float startX, float startY) {
        ArrayList<Result> results = new ArrayList<>();
        for (int i = 0; i< mOutputRow; i++) {
            if (outputs[i* mOutputColumn +4] > mThreshold) {
                float x = outputs[i* mOutputColumn];
                float y = outputs[i* mOutputColumn +1]-70;
                float w = outputs[i* mOutputColumn +2];
                float h = outputs[i* mOutputColumn +3];

                float left = imgScaleX * (x - w/2);
                float top = imgScaleY * (y - h/2);
                float right = imgScaleX * (x + w/2);
                float bottom = imgScaleY * (y + h/2);

                float max = outputs[i* mOutputColumn +5];
                int cls = 0;
                for (int j = 0; j < mOutputColumn -5; j++) {
                    if (outputs[i* mOutputColumn +5+j] > max) {
                        max = outputs[i* mOutputColumn +5+j];
                        cls = j;
                    }
                }
                Rect raw_rect = new Rect((int)left, (int)top, (int)right, (int)bottom);
                Rect rect = new Rect((int)(startX+ivScaleX*left), (int)(startY-120+top*ivScaleY), (int)(startX+ivScaleX*right), (int)(startY-120+ivScaleY*bottom));
                Result result = new Result(cls, outputs[i*mOutputColumn+4], rect, raw_rect);
                results.add(result);
            }
        }
        return nonMaxSuppression(results, mNmsLimit, iouThreshold);
    }

    static ArrayList<Result> outputsToNMSPredictions_graph(float[] outputs, float ivScaleX, float ivScaleY, float startX, float startY,
                                                           float subStartX, float subStartY, float subScaleX, float subScaleY) {
        ArrayList<Result> results = new ArrayList<>();
        for (int i = 0; i< mOutputRow; i++) {
            if (outputs[i* mOutputColumn_graph +4] > mThreshold) {
                float x = outputs[i* mOutputColumn_graph];
                float y = outputs[i* mOutputColumn_graph +1]-70;
                float w = outputs[i* mOutputColumn_graph +2];
                float h = outputs[i* mOutputColumn_graph +3];

                float left = subStartX + subScaleX * (x - w/2);
                float top = subStartY + subScaleY * (y - h/2);
                float right = subStartX + subScaleX * (x + w/2);
                float bottom = subStartY + subScaleY * (y + h/2);

                float max = outputs[i* mOutputColumn_graph +5];
                int cls = 0;
                for (int j = 0; j < mOutputColumn_graph -5; j++) {
                    if (outputs[i* mOutputColumn_graph +5+j] > max) {
                        max = outputs[i* mOutputColumn_graph +5+j];
                        cls = j;
                    }
                }
                Rect raw_rect = new Rect((int)left, (int)top, (int)right, (int)bottom);
                Rect rect = new Rect((int)(startX-110+ivScaleX*left), (int)(startY-100+top*ivScaleY), (int)(startX-110+ivScaleX*right), (int)(startY-100+ivScaleY*bottom));
                Result result = new Result(cls, outputs[i*mOutputColumn_graph+4], rect, raw_rect);
                results.add(result);
            }
        }
        return nonMaxSuppression(results, mNmsLimit, iouThreshold);
    }
}
