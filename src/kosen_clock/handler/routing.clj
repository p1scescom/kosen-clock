(ns kosen-clock.handler.routing
  (:import  [javax.imageio ImageIO]
            [java.io ByteArrayOutputStream]
            [java.awt.image BufferedImage]
            [java.util Arrays Date]
            [java.text SimpleDateFormat])
  (:require [clojure.java.io :as io]
            [compojure.core :refer [GET POST context routes]]
            [compojure.route :as route]
            [integrant.core :as ig]
            [kosen-clock.info :as info]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as res]
            [selmer.parser :as parser]))


(def access-root (atom ""))

(def img-root (atom "img/show"))

(def global-last-upload-image (atom (first (reverse (sort (.list (io/file @img-root)))))))

(def no-image (io/input-stream (io/resource "kosen_clock/public/img/no-image.jpg"))
  #_(let [strm ^BufferedImage (ImageIO/read (io/resource "kosen_clock/public/img/no-image.jpg"))
                    baos (ByteArrayOutputStream.)]
                (ImageIO/write strm "JPEG" baos)
                (.toString baos)))

(defn upload-html [image-name]
  (parser/render-file
   "template/upload.html" {:access-root @access-root
                           :last-image  (if (and (boolean image-name))
                                           image-name
                                           "no-image.jpg")}))
(defn img-time-name []
  (str "IMG-" (.format (SimpleDateFormat. "yyyy:MM:dd-HH:mm:ss-") (Date.)) (rand-int 65536)))

(defn upload-image [file cookies]
  (let [filename (file :filename)]
    (if (and (not (.isEmpty filename)) (re-find #"(?i)\.(jpg|jpeg|png)$" filename))
      (do
        (let [now-label (img-time-name)]
          (when-not (.exists (io/as-file @img-root)) (.mkdirs (io/file @img-root)))
          (io/copy (file :tempfile) (io/file (str @img-root now-label)))
          (reset! global-last-upload-image now-label)
          {:cookies {"img" now-label}
          :headers {"Content-Type" "text/html; charset=utf-8"}
          :body (upload-html now-label)}))
      (upload-html (:value (cookies "img"))))))

(defmethod ig/init-key :kosen-clock.handler/routing [_ _]
  (context @access-root []
    (wrap-cookies
    (wrap-multipart-params
      (routes
       (GET "/" [] (parser/render-file "template/root.html" {:access-root @access-root
                                                             :global-last-upload-image @global-last-upload-image}))
        (GET "/upload" {cookies :cookies}
              (upload-html (:value (cookies "img"))))
        (POST "/upload" {params :params cookies :cookies} (upload-image (get params "file") cookies))
        (GET "/list" [] (parser/render-file "template/list.html" {:access-root @access-root}))

        (context "/img" []
                 (route/files "/" {:root @img-root})
                 (route/resources "/" {:root "kosen_clock/public/img"})
                 (route/not-found {:headers {"Content-Type" "image/jpeg"}
                                   :body (io/input-stream (io/resource "kosen_clock/public/img/no-image.jpg"))}))
        (route/resources "/public/" {:root "kosen_clock/public"})
        (route/resources "/fonts/" {:root "kosen_clock/public/fonts"})
        (route/resources "/test/" {:root "kosen_clock/public/test"})
        (route/resources "/css/" {:root "kosen_clock/public/css"})
        (route/resources "/js/" {:root "kosen_clock/public/js"})
      )))))
