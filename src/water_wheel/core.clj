(ns water_wheel.core
  (:require [quil.core :as q]
            [quil.middleware :as m]))

(def center-x 540.0)
(def center-y 360.0)
(def base-moment 200000.0)
(def start-momentum 0)
(def time-step 0.1)
(def fill-rate 1.0)
(def drain-rate -0.042
  )
(def bin-cap 5000.0)
(def friction-loss 170000)
(def num-of-bins 8)



(use 'clojure.test)

(defn print_n_return [pre obj post]
  (println pre) 
  (println obj) 
  (println post)
  obj)

(with-test
  (defn angle-and-length-to-position [{angle :angle length :length}]
    (into {} (map (fn [entry] 
      ( if (= :x (first entry))
        [(first entry) (second entry)]
        [(first entry) (* -1 (second entry))])) 
    {
     :x (q/round (* length (q/cos (q/radians angle))))
     :y (q/round (* length (q/sin (q/radians angle))))
    })))
  (is (= {:x 2.0 :y 0.0} (angle-and-length-to-position {:angle 0 :length 2}))))



(defn evenly-divide-a-circle 
  "Returns a vector of evenly spaced angles"
  [num-divisions previous-angle]
  (let [angle-inc (/ 360.0 num-divisions)]
    (map #(mod (+ (* %1 angle-inc) previous-angle) 360) (range num-divisions)))
  )




(defn position-relative-to-center [position]
  {:x (+ center-x (:x position)) :y (+ center-y (:y position))}
  )




;Need to fix this using x-left x-right
(defn draw-bucket [bin]
  (let [{x :x y :y} bin]
   (q/stroke 212 161 106)
   (q/fill 255 255 255 0)
  (q/quad (- x 50) (- y 50) (+ x 50) (- y 50) (+ x 50) (+ y 50) (- x 50) (+ y 50))  
  ;Now fill the bucket
  (let [percent (* 100.0 (/ (bin :grams-of-water) bin-cap))]
    (q/stroke 0 0 255 0)
    (q/fill 0 0 255 50)
    (let [bot-y (+ y 50) top-y (- (+ y 50) percent)]
      (q/quad (- x 50) top-y (+ x 50) top-y (+ x 50) bot-y (- x 50) bot-y)))))




(defn create-default-source []
  {:fill-rate 10 :x-left (- center-x 25)  :x-right (+ center-x 25)}
  )




(defn create-wheel [num-bins length wheel-angle]
  (assoc {} :angle wheel-angle :length length :bins
    (map (fn [bin] (assoc bin :x-left (- (bin :x) 50) :x-right (+ (bin :x) 50))) 
      (map #(position-relative-to-center %1)
        (map (fn [angle] (angle-and-length-to-position (identity {:angle angle :length length}))) 
          (evenly-divide-a-circle num-bins wheel-angle))))))




(defn fill-bins [wheel water-levels]
  (println "\n\n\n")
  (println "wheel: ")
  (println wheel)
  (println "water levels: ")
  (println water-levels)
  (print_n_return "after_fill_bins" (map (fn [bin water-level] 
         (let [new-water-level (+ water-level (get bin :grams-of-water 0))]                       
           (if (> new-water-level 0.0) 
              (if (< new-water-level bin-cap) 
                (assoc bin :grams-of-water new-water-level)
                bin
                )
              (assoc bin :grams-of-water 0))))
  (wheel :bins) water-levels) "\n\n\n" ))



(defn determine-fill [wheel source]
  ;(println "in determine fill")
  ;(println wheel)
  ;(println source)
  (map (fn [bin] 
         (if (not (or 
                  (> (source :x-left) (bin :x-right)) 
                  (< (source :x-right) (bin :x-left))
                  (> (bin :y) center-y )
                  ))
            fill-rate  
            drain-rate))
     (wheel :bins)))




(defn draw-water-wheel [wheel src]
  (q/stroke 212 161 106)
  (q/text (str "Wheel Angle: " (:angle wheel)) 20 20)
  (q/text (str "d/fill: " (apply str (determine-fill wheel src))) 20 40)
  (doseq [bin (:bins wheel)]
    (q/stroke 212 161 106)
    (q/line center-x center-y (:x bin) (:y bin))
    (draw-bucket bin)) 
  (q/fill 255 255 255 0)
  (q/stroke 128 76 21)
  (q/ellipse center-x center-y 150 150)) 



(defn draw-source [source]
  (q/stroke 128 76 21)
  (q/quad (source :x-right) 10 (source :x-left) 10 (source :x-left) 40 (source :x-right) 40))

(with-test 
  (defn calculate-single-torque  [center bin]
    (let [x (bin :x) y (bin :y) force (bin :newtons-of-water) c-x (center :x) c-y (center :y)]

    ;Multiply the distance from center by the normalized component
    ;and finally by the force of water due to gravity
    (* 
      
      force 
      
      ;Get the distance from center
      (q/dist x y c-x c-y) 

      ;Subtract 90 and then take sin of it to get the normalized component
      (if (< (q/abs (- x c-x)) 0.001)
        0.0
        (q/sin 
          (q/radians (-
            (+ 
              ;To make up for the -pi/2 - pi/2 range
              (if (< (- x c-x) 0)
                180  
                0
                )
            ;Get the line angle
              (q/degrees (q/atan (/ (- y c-y) (- x c-x)))))
            90 )))))))
  ;0deg case
  (is (= (* -20.0 30.0 ) (calculate-single-torque {:x 20  :y 20} 
                                             {:x 50 :y 20 :newtons-of-water 20})))
  ;90deg case
  (is (= 0.0 (calculate-single-torque {:x 20 :y 20}
                                    {:x 20 :y 50 :newtons-of-water 42})))
  ;180deg case
  (is (= (* 20.0 30.0) (calculate-single-torque {:x 20 :y 20}
                                           {:x -10 :y 20 :newtons-of-water 20}))))

(with-test
  (defn calculate-moment [{length :length bins :bins}] ;pass a wheel
    (+ base-moment (reduce 
      (fn [val bin]
              (+ val (* (q/sq length) (bin :grams-of-water))))
      0 bins)))
  (is (= 3000.0 (calculate-moment 
              {:length 10 :bins [{:grams-of-water 20} {:grams-of-water 0} {:grams-of-water 10}]}))))


(defn grams-to-newtons-bin [bin]
  (assoc bin :newtons-of-water (* (bin :grams-of-water) 9.8)))

(defn spin-wheel [wheel angle]
  ;(println wheel)
  ;(println angle)
  (let [spun-wheel (create-wheel (count (wheel :bins)) 200 (+ (wheel :angle) angle))]
    (assoc spun-wheel 
      :bins (map 
        (fn [bin old-bin] (assoc bin :grams-of-water (:grams-of-water old-bin)))
        (spun-wheel :bins) (wheel :bins)) 
      :angular-momentum (wheel :angular-momentum start-momentum))))

(with-test
  (defn apply-friction-to-torque [friction total-torque]
    (if (= 0 total-torque)
      0
      (if (< total-torque 0)
        (if (> (+ total-torque friction) 0) ;We don't want friction to "apply torque" only resist
          0 ;Friction holds wheel in place
          (+ total-torque friction))
        (if (< (- total-torque friction) 0)
          0 ;Friction holds wheel in place
          (- total-torque friction)))))
  (is (= 0 (apply-friction-to-torque 2000.0 -1500.0))) 
  (is (= -500.0 (apply-friction-to-torque 2000.0 -2500.0) ))
  (is (= 0 (apply-friction-to-torque 200.0 150.0)))
  (is (= 5.0 (apply-friction-to-torque 25.0 30.0))))

(defn apply-friction-to-momentum [friction t momentum]
  (let [momentum-change (* friction t)]
    (apply-friction-to-torque momentum-change momentum)))


(defn update-wheel-state [wheel]
  ;(println wheel)
  (let [torque (apply-friction-to-torque friction-loss
                               (reduce + 
                        (print_n_return "total_torque: "(map 
                          (fn [bin] (calculate-single-torque {:x center-x :y center-y} (grams-to-newtons-bin bin))) (wheel :bins)) "")))
        moment (calculate-moment wheel)]
  ;angular_momentum = old_angular_momentum + (tourque * time_step)
  ;angle = (old_angular_momentum/moment*time_step) + .5*(torque/moment)*t^2

    (let [d-angle (+ (* (wheel :angular-momentum start-momentum) (/ 1.0 moment) time-step)
                     (* 0.5 (/ torque moment) (q/sq time-step)))]
          (assoc 
            (spin-wheel wheel d-angle)
            :angular-momentum (+ (apply-friction-to-momentum friction-loss time-step (wheel :angular-momentum start-momentum)) (* torque  time-step)))))) 



(defn draw-water [source fill-array]
  (q/stroke 0 0 255 0)
  (q/fill 0 0 255 50)
  (if (> 0 (reduce + fill-array))
  (q/quad (source :x-right) 40 (source :x-left) 40 (source :x-left) 290 (source :x-right) 290)  
  (q/quad (source :x-right) 40 (source :x-left) 40 (source :x-left) 100 (source :x-right) 100)))


(defn update-state [state]
  ; Update sketch state by changing circle color and position.
  ;(println "In update-state")
  ;(println (state :wheel))
  (loop [loop-state state loop-num 0]
    (let [wheel (update-wheel-state 
    (assoc (loop-state :wheel) :bins 
      (fill-bins
        (loop-state :wheel)
        (determine-fill (loop-state :wheel) (create-default-source)))))] 
  ;(println "After update-wheel-state")
  ;(println wheel)
  (if (< loop-num 10)
    (recur {:wheel wheel  :source (create-default-source) :fill-array (determine-fill wheel (create-default-source))} (+ 1 loop-num))
    loop-state))))



(defn draw-state [state]
  (q/background 255 255 255)
  (q/stroke-weight  10)       ;; Set the stroke thickness randomly
  (q/fill 0 0 0 ) 
  ; Clear the sketch by filling it with light-grey color.
  ; Draw the wheel
  (draw-water-wheel (state :wheel) (state :source))
  (draw-source (state :source))
  (draw-water (state :source) (state :fill-array)))
 

(defn setup []
  ; Set frame rate to 30 frames per second.
  (q/frame-rate 30)
  ; Set color mode to HSB (HSV) instead of default RGB.
  (q/color-mode :rgb)
  ; setup function returns initial state. It contains
  ; circle color and position.
  {:wheel (create-wheel num-of-bins 200 20) :source (create-default-source)})


(q/defsketch water_wheel
  :title "Non Linear Water Wheel"
  :size [1080 720]
  ; setup function called only once, during sketch initialization.
  :setup setup
  ; update-state is called on each iteration before draw-state.
  :update update-state
  :draw draw-state
  :features [:keep-on-top]
  ; This sketch uses functional-mode middleware.
  ; Check quil wiki for more info about middlewares and particularly
  ; fun-mode.
  :middleware [m/fun-mode])
