(ns kosen-clock.handler.routing
  (:import  [javax.imageio ImageIO]
            [java.io ByteArrayOutputStream]
            [java.awt.image BufferedImage]
            [java.util Arrays Date]
            [java.text SimpleDateFormat]
            [java.time  LocalDateTime])
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async :refer [>! <! chan close! go go-loop timeout]]
            [compojure.core :refer [GET POST context routes]]
            [compojure.route :as route]
            [integrant.core :as ig]
            [kosen-clock.info :as info]
            [kosen-clock.img :as img]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [selmer.parser :as parser]))

(def access-root (atom ""))

(def img-root (atom "img/show/"))

(def img-local (atom "img/local/"))

(def no-image (io/input-stream (io/resource "kosen_clock/public/img/no-image.jpg")))

(def global-last-upload-image (atom (last (sort (.list (io/file @img-root))))))

(def time-now (promise))

(def local-imgs (atom (vec (sort (.list (io/file @img-local))))))

(def root-imgs (atom (vec (sort (.list (io/file @img-root))))))

(def making-img (atom #{}))

(def img-ch (chan))

(def ul-ch (atom nil))

(def gc-ch (atom nil))

(def mc-ch (atom nil))

(defn upload-loop []
  (go-loop []
    (let [target (<! img-ch)]
      (swap! making-img conj (:file-name target))
      (when-not (.exists (io/as-file @img-root)) (.mkdirs (io/file @img-root)))
      (when-not (.exists (io/as-file @img-local)) (.mkdirs (io/file @img-local)))
      (io/copy (:tempfile target) (io/file (str @img-local (:file-name target))))
      (swap! local-imgs conj (:file-name target))
      (img/make (:file-name target) @img-root @img-local)
      (swap! making-img disj (:file-name target))
      (swap! root-imgs conj (str "time-" (:file-name target)))
      (recur))))

(defn gc-loop []
  (go-loop []
    (<! (timeout 300000))
    (System/gc)))

(defn make-clock []
  (go-loop [bef-sec nil]
    (<! (timeout 1000))
    (let [s (.getSecond (LocalDateTime/now))]
      (when (or (nil? bef-sec) (< s bef-sec))
        (println "-- make-clock --" "bef:" bef-sec " s:" s)
        (img/make (rand-nth @local-imgs) "time-now.jpg" @img-root @img-local))
      (recur s))))

(defn init []
  (when (nil? @ul-ch) (reset! ul-ch (upload-loop)))
  (when (nil? @gc-ch) (reset! gc-ch (gc-loop)))
  (when (nil? @mc-ch) (reset! mc-ch (make-clock))))

(defn end-loops []
  (dorun (map #(when-let [target %] (close! target)) (list @ul-ch @gc-ch @mc-ch))))

(defn img-time-name [mark]
  (str "IMG-" (.format (SimpleDateFormat. "yyyy:MM:dd-HH:mm:ss-") (Date.)) (rand-int 65536) "-" mark ".jpg"))

(defn upload-html [args]
  (let [last-markA (:last-markA args)
        last-markB (:last-markB args)
        last-markC (:last-markC args)
        error      (:error args)]
    (parser/render-file
    "template/upload.html" {:access-root @access-root
                            :last-markA (if (and (boolean last-markA) (not= "" last-markA))
                                          last-markA
                                          "no-image.jpg")
                            :last-markB (if (and (boolean last-markB) (not= "" last-markB))
                                          last-markB
                                          "no-image.jpg")
                            :last-markC (if (and (boolean last-markC) (not= "" last-markC))
                                          last-markC
                                          "no-image.jpg")
                            :error error})))

(def mark-list (list "markA" "markB" "markC"))

(defn upload-image [mark file cookies]
  (let [filename (file :filename)
        tempfile (file :tempfile)
        last-marks (apply hash-map (interleave mark-list (map #(:value %) (map cookies mark-list))))
        file-name (img-time-name mark)
        new-label (str "time-" file-name)
        not-mark (not (some #(= mark %) mark-list))
        not-file (.isEmpty filename)
        not-picture (not (re-find #"(?i)\.(jpg|jpeg)$" filename))
        large-picture (< 10485760 (.length tempfile))
        all-check (or not-mark not-file not-picture large-picture)]
    (when-not all-check
      (go (>! img-ch {:file-name file-name
                     :tempfile  tempfile})))
    {:status (if all-check
               400
               200)
     :cookies {"markA" (if (or all-check (not= mark "markA"))
                         (if (nil? (last-marks "markA")) "" (last-marks "markA"))
                         new-label)
               "markB" (if (or all-check (not= mark "markB"))
                         (if (nil? (last-marks "markB")) "" (last-marks "markB"))
                         new-label)
               "markC" (if (or all-check (not= mark "markC"))
                         (if (nil? (last-marks "markC")) "" (last-marks "markC"))
                         new-label)}
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (upload-html {:last-markA
                         (if (or all-check (not= mark "markA"))
                           (last-marks "markA")
                           new-label)
                         :last-markB
                         (if (or all-check (not= mark "markB"))
                           (last-marks "markB")
                           new-label)
                         :last-markC
                         (if (or all-check (not= mark "markC"))
                           (last-marks "markC")
                           new-label)
                         :error (cond
                                  not-mark "<h3 class=\"text-danger\">存在しないマークを選択しています。</h3>"
                                  not-file "<h3 class=\"text-danger\">ファイルを選択して送信してください。</h3>"
                                  not-picture "<h3 class=\"text-danger\">拡張子が画像ファイルではありません。.jpg 拡張子にしてください。</h3>"
                                  large-picture "<h3 class=\"text-danger\">画像ファイルが大き過ぎます。10MB以下でお願いします。</h3>"
                                  :else nil)})}))

(defmethod ig/init-key :kosen-clock.handler/routing [_ _]
  (context @access-root []
    (wrap-cookies
    (wrap-multipart-params
      (routes
       (GET "/" [] (parser/render-file "template/root.html"
                                       {:access-root @access-root
                                        :global-last-upload-image @global-last-upload-image}))
        (GET "/upload" {cookies :cookies}
             (upload-html {:last-markA (:value (cookies "markA"))
                           :last-markB (:value (cookies "markB"))
                           :last-markC (:value (cookies "markC")) }))
        (POST "/upload" {params :params cookies :cookies}
              (upload-image (get params :mark) (get params "file") cookies))
        (GET "/list" [] (parser/render-file "template/list.html" {:access-root @access-root}))

        (context "/img" []
                 (route/files "/" {:root @img-root})
                 (route/resources "/" {:root "kosen_clock/public/img"})
                 (route/not-found {:headers {"Content-Type" "image/jpeg"}
                                   :body no-image}))
        (route/resources "/public/" {:root "kosen_clock/public"})
        (route/resources "/fonts/" {:root "kosen_clock/public/fonts"})
        (route/resources "/test/" {:root "kosen_clock/public/test"})
        (route/resources "/css/" {:root "kosen_clock/public/css"})
        (route/resources "/js/" {:root "kosen_clock/public/js"}))))))
