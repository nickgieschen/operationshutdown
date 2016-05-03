;; This script allow you to either generate a file matching players to draft rounds or generate
;; files representing players available in the draft. Uncomment the fn at the end of the file
;; for the functionality you want to run.


(ns oshut.core
  (:require [clojure-csv.core :as csv]
            [clojure.java.io :as io]
            [clojure.string :as st]))

(def progression {0 25
                  1 1
                  2 2
                  3 2
                  4 3
                  5 3
                  6 4
                  7 5
                  8 5
                  9 6
                  10 7
                  11 7
                  12 8
                  13 9
                  14 10
                  15 11
                  16 12
                  17 13
                  18 14
                  19 15
                  20 16
                  21 16
                  22 17
                  23 18
                  24 19
                  25 20})

;(defn load-majl-rosters [] (-> "rosters.csv" io/reader csv/parse-csv {:delimiter \;}))

(defn load-majl-rosters [] (csv/parse-csv (io/reader "rosters.csv") :delimiter \; ))

(defn load-draft [] (-> "draft.csv" io/reader csv/parse-csv))

(defn load-keepers [] (-> "keepers.csv" io/reader csv/parse-csv))

(defn load-minl-rosters [] (-> "minor-league-rosters.csv" io/reader csv/parse-csv))

(defn load-all-players [proj pos] (-> (str proj "-" pos ".csv") io/reader csv/parse-csv))

(defn get-round [name] (loop [draft (load-draft) round-number 0 res []]
                         (let [round (first draft)]
                           (if (= round nil) res
                             (let [next-res (if (nil? (some #{(st/lower-case name)} (map st/lower-case round))) res (conj res round-number))]
                               (recur (rest draft) (inc round-number) next-res))))))

(defn unspanishfy [name] (-> name
                             (st/replace "í" "i")
                             (st/replace "í" "i")
                             (st/replace "ó" "o")
                             (st/replace "é" "e")
                             (st/replace "á" "a")))

(defn translate-round [round-tup] (if (= (count round-tup) 1)
                                    (let [unbound-round (first round-tup)
                                          round (if (> unbound-round 25) 0 unbound-round)]
                                      [round (get progression round)])
                                    ["" ""]))


(defn get-round-info [name] (translate-round (get-round (unspanishfy name))))

(defn match-players-to-rounds []
  (map #(if (= (take 2 (first %)) [\T \:])
          (concat % ["" ""])
          (concat % (get-round-info (first %)))) (load-majl-rosters)))

(defn generate-draft-rounds []
  (spit "DraftRounds.tsv" (reduce #(str %1 "\n" (st/join "\t" %2)) "" (match-players-to-rounds))))

(defn after-colon [name]
  (let [split (split-with #(not= %1 \:) name)]
    (if-not (empty? (second split))
      (apply str (rest (second split)))
      name)))

(defn before-paren [name]
   (apply str (first(split-with #(not= %1 \() name))))

(defn before-comma [name]
   (apply str (first(split-with #(not= %1 \,) name))))

(defn remove-periods [name]
  (apply str (remove #(= %1 \.) name)))

(defn norm-name [norm-fn name]
  (-> name
      remove-periods
      norm-fn
      st/trim
      st/lower-case
      unspanishfy))

(defn flatten-table [table trim-header? norm-fn]
  (map (partial norm-name norm-fn) (filter #(not= %1 "") (flatten (if [trim-header?] (rest table) table)))))

(defn get-all-kept []
  (concat (flatten-table (load-minl-rosters) true (comp before-paren before-comma)) (flatten-table (load-keepers) true after-colon)))

(defn generate-avail [proj pos]
  (let [keepers (get-all-kept)
        players (load-all-players proj pos)]
       (remove (fn [player-data]
                   (some #(= (norm-name identity %1) (norm-name identity (get player-data 4))) keepers)) players)))

(defn spit-avail [proj pos]
  (spit (str "avail-" proj "-" pos ".csv") (reduce #(str %1 "\n" (apply str (interpose "," %2))) "" (generate-avail proj pos))))

;(spit-avail "pecota" "pitchers")
;(generate-draft-rounds)


