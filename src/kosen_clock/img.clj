(ns kosen-clock.img
  (:require [clojure.java.io :as io]
            [clojure.string :as string :refer [split]])
  (:import [org.opencv.core Core DMatch KeyPoint Mat MatOfFloat MatOfKeyPoint MatOfInt MatOfInt4 MatOfDMatch MatOfRect MatOfPoint Point Rect Scalar Size]
           [org.opencv.imgproc Imgproc]
           [org.opencv.imgcodecs Imgcodecs]
           [org.opencv.features2d AKAZE DescriptorMatcher Features2d FeatureDetector]
           [java.lang Math]
           [java.time  LocalDateTime]
           [java.time.format DateTimeFormatter]))

(clojure.lang.RT/loadLibrary Core/NATIVE_LIBRARY_NAME)

(def marks {"markA" (Imgcodecs/imread (.getPath (io/resource "kosen_clock/private/img/fes52ndmarkA.png")))
            "markB" (Imgcodecs/imread (.getPath (io/resource "kosen_clock/private/img/fes52ndmarkB.jpg")))
            "markC" (Imgcodecs/imread (.getPath (io/resource "kosen_clock/private/img/fes52ndmarkC.jpg")))})

(def fonts {"markA" Core/FONT_HERSHEY_SCRIPT_SIMPLEX
            "markB" Core/FONT_HERSHEY_TRIPLEX
            "markC" Core/FONT_HERSHEY_SCRIPT_COMPLEX})

(def asyukuritu (MatOfInt. (int-array [Imgcodecs/CV_IMWRITE_JPEG_QUALITY 50])))

(defn output [directory name img]
  (Imgcodecs/imwrite (str "./" directory name) img asyukuritu))

(defn make
  ([target-name img-root img-local]
   (make target-name (str "time-" target-name) img-root img-local))
  ([target-name output-name img-root img-local]
   (let [now-time (let [t (LocalDateTime/now)
                       formatter (DateTimeFormatter/ofPattern "HH:mm")]
                   (-> t (.format formatter)))
        dst (Imgcodecs/imread (str "./" img-local target-name))
        mark-str (last (split (first (split target-name #"\.")) #"-"))
        mark (marks mark-str)
        dst-gray (Mat.)
        dst-bin (Mat.)

        labelimg (Mat.)
        stats (Mat.)
        stats-array (int-array 5)
        centroids (Mat.)
        cent-array (double-array 2)

        roiRects (atom [])]
    (println "in img:" (str img-local target-name))
    (Imgproc/cvtColor dst dst-gray Imgproc/COLOR_BGR2GRAY)
    (Imgproc/adaptiveThreshold dst-gray dst-bin 255 Imgproc/ADAPTIVE_THRESH_GAUSSIAN_C Imgproc/THRESH_BINARY (let [tmp (int (/ (min (.width dst) (.height dst)) 5))] (if (odd? tmp) tmp (dec tmp))) 2.0)

    (Imgproc/morphologyEx dst-bin dst-bin Imgproc/MORPH_OPEN (Mat.) (Point. -1 -1) 2)
    (let [nlabels (Imgproc/connectedComponentsWithStats dst-bin labelimg stats centroids)]
      (dorun (map (fn [n]
                    (do (-> stats (.row n) (.get 0 0 stats-array))
                      (let [get-stats (map #(aget ^ints stats-array %) (range 0 4))]
                        (swap! roiRects conj (apply #(Rect. %1 %2 %3 %4) get-stats)))))
                  (range 1 nlabels)))
      (let [dst-clone (.clone dst)]
        (when-not (zero? (count @roiRects))
          (swap! roiRects (fn [rects] (filter (fn [r]
                                      (let [sm (.submat dst-bin r)
                                            similarity (Imgproc/matchShapes mark sm Imgproc/CV_CONTOURS_MATCH_I1 0)]
                                        (.release sm)
                                        (when (and (< similarity 0.05 ) (< (Math/abs (float (- 1 (max (/ (.height r) (.width r)) (/ (.width r) (.height r)))))) 0.1))
                                          true)))
                                      rects)))
        (when-not (zero? (count @roiRects))
          (swap! roiRects (fn [rects]
                            (let [average (/ (apply + (map #(min (.width %) (.height %)) rects)) (count rects))]
                              (filter #(<= average (min (.width %) (.height %))) rects))))
          (when-not (zero? (count @roiRects))
            (swap! roiRects (fn [target] (sort-by #(min (.width %) (.height %)) > target)))
            (dorun (map (fn [r]
                          (let [sm (.submat dst r)
                                smc (.submat dst-clone r)
                                tmp (Mat.)]
                            (Imgproc/GaussianBlur smc smc (Size. 31 31) 8 6)
                            (.release sm)
                            (.release smc)
                            (.release tmp)))
                        @roiRects))
            (swap! roiRects #(take 5 %))
            (swap! roiRects (fn [target] (sort-by #(Math/abs (float (- (/ (.width dst-clone) 2) (+ (.x %) (/ (.width %) 2))))) < target)))
            (let [r (first @roiRects)]
              (Imgproc/putText dst-clone now-time (Point. (float (- (.x r) (/ (.width r) 2) (* 1.25 (.height r)))) (+ (.y r) (.height r))) (fonts mark-str) (float (/ (.height r) 20)) (Scalar. 255 255 255) 40 Core/LINE_AA false)
              (Imgproc/putText dst-clone now-time (Point. (float (- (.x r) (/ (.width r) 2) (* 1.25 (.height r)))) (+ (.y r) (.height r))) (fonts mark-str) (float (/ (.height r) 20)) (Scalar. 0 0 0) 20 Core/LINE_AA false))
            (output img-root output-name dst-clone))))))

    (.release dst)
    (.release dst-gray)
    (.release dst-bin)

    (.release labelimg)
    (.release stats)
    (.release centroids)))
  )

(defn init []
  (dorun (map
          (fn [m-map]
            (let [m (val m-map)
                  m-gray (Mat.)
                  m-bin (Mat.)]
              (Imgproc/cvtColor m m-gray Imgproc/COLOR_BGR2GRAY)
              (Imgproc/threshold m-gray m-bin 128 255 Imgproc/THRESH_BINARY)
              (Imgproc/morphologyEx m-bin m Imgproc/MORPH_OPEN (Mat.) (Point. -1 -1) 2)
              (.release m-gray)
              (.release m-bin)))
          marks)))
