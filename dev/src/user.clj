(ns user
  (:require [clojure.java.io :as io]
            [selmer.parser :as parser]))

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (parser/set-resource-path! (io/resource "../resources/kosen_clock"))
  (require 'dev)
  (in-ns 'dev)
  :loaded)
