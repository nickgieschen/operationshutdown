;; A script to match players to get-rounds
;; Requires a file called rosters.csv which includes all the players. Should be of the form:
;;   T: [Team Name]
;;   [Player Name], [Team and positions]
;;
;; E.g.
;;   T: SNK Crushers
;;   Anthony Rendon, (WAS - 2B,3B)
;;
;; Also requires a file called draft.csv which contains the previous years draft info. It is simply a table with teams as columns and
;; players in the rows where the row number represents the round they were drafted in.

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

(defn load-majl-rosters [] (-> "rosters.csv" io/reader csv/parse-csv))

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
      ;remove-periods
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
                   (some #(= %1 (norm-name identity (first player-data))) keepers)) players)))

(defn spit-avail [proj pos]
   (spit (str "avail-" proj "-" pos ".csv") (reduce #(str %1 "\n" (apply str (interpose "," %2))) "" (generate-avail proj pos))))

(spit-avail "zips" "pitchers")
