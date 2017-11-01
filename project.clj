(defproject kosen-clock "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.443"]
                 [duct/core "0.3.3"]
                 [duct/module.logging "0.2.0"]
                 [duct/module.web "0.5.0"]
                 [selmer "1.10.9"]]
  :plugins [[duct/lein-duct "0.9.0-alpha8"]]
  :main ^:skip-aot kosen-clock.main
  :uberjar-name "kosen-clock-standalone.jar"
  :duct {:config-paths ["resources/kosen_clock/config.edn"]}
  :jvm-opts ["-Djava.library.path=lib"]
  :resource-paths ["resources" "target/resources" "lib" "lib/opencv-320.jar"]
  :prep-tasks     ["javac" "compile" ["duct" "compile"]]
  :injections [(clojure.lang.RT/loadLibrary org.opencv.core.Core/NATIVE_LIBRARY_NAME)]
  :profiles
  {:dev     [:project/dev :profiles/dev]
   :repl    {:prep-tasks   ^:replace ["javac" "compile"]
             :repl-options {:init-ns user}}
   :uberjar {:aot :all}
   :profiles/dev {}
   :project/dev  {:source-paths   ["dev/src"]
                  :resource-paths ["dev/resources"]
                  :dependencies   [[integrant/repl "0.2.0"]
                                   [eftest "0.3.0"]
                                   [kerodon "0.8.0"]]}})
