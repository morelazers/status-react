(ns status-im.hardwallet.core
  (:require [re-frame.core :as re-frame]
            status-im.hardwallet.fx
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.config :as config]
            [status-im.utils.fx :as fx]
            [status-im.utils.platform :as platform]
            [taoensso.timbre :as log]
            [status-im.i18n :as i18n]
            [status-im.accounts.create.core :as accounts.create]
            [status-im.accounts.login.core :as accounts.login]))

(defn hardwallet-supported? [{:keys [db]}]
  (and config/hardwallet-enabled?
       platform/android?
       (get-in db [:hardwallet :nfc-supported?])))

(fx/defn navigate-to-authentication-method
  [cofx]
  (if (hardwallet-supported? cofx)
    (navigation/navigate-to-cofx cofx :hardwallet-authentication-method nil)
    (accounts.create/navigate-to-create-account-screen cofx)))

(fx/defn on-register-card-events
  [{:keys [db]} listeners]
  {:db (update-in db [:hardwallet :listeners] merge listeners)})

(fx/defn on-get-application-info-success
  [{:keys [db]} info]
  (let [info' (js->clj info :keywordize-keys true)]
    {:db (-> db
             (assoc-in [:hardwallet :application-info] info')
             (assoc-in [:hardwallet :application-info :applet-installed?] true)
             (assoc-in [:hardwallet :application-info-error] nil))}))

(fx/defn on-get-application-info-error
  [{:keys [db]} error]
  (log/debug "[hardwallet] application info error " error)
  {:db (-> db
           (assoc-in [:hardwallet :application-info-error] error)
           (assoc-in [:hardwallet :application-info :applet-installed?] false))})

(fx/defn set-nfc-support
  [{:keys [db]} supported?]
  {:db (assoc-in db [:hardwallet :nfc-supported?] supported?)})

(fx/defn set-nfc-enabled
  [{:keys [db]} enabled?]
  {:db (assoc-in db [:hardwallet :nfc-enabled?] enabled?)})

(fx/defn navigate-to-connect-screen [cofx]
  (fx/merge cofx
            {:hardwallet/check-nfc-enabled    nil
             :hardwallet/register-card-events nil}
            (navigation/navigate-to-cofx :hardwallet-connect nil)))

(fx/defn success-button-pressed [cofx]
  ;; login not implemented yet
)

(fx/defn error-button-pressed [{:keys [db] :as cofx}]
  (let [return-to-step (get-in db [:hardwallet :return-to-step] :begin)]
    (fx/merge cofx
              {:db (assoc-in db [:hardwallet :setup-step] return-to-step)}
              (when-not return-to-step
                (navigation/navigate-to-cofx :hardwallet-connect nil)))))

(fx/defn load-pairing-screen [{:keys [db]}]
  {:db       (assoc-in db [:hardwallet :setup-step] :pairing)
   :dispatch [:hardwallet/pair]})

(fx/defn pair [cofx]
  (let [{:keys [password]} (get-in cofx [:db :hardwallet :secrets])]
    {:hardwallet/pair {:password password}}))

(fx/defn return-back-from-nfc-settings [{:keys [db]}]
  (when (= :hardwallet-connect (:view-id db))
    {:hardwallet/check-nfc-enabled nil}))

(defn- proceed-to-pin-confirmation [fx]
  (assoc-in fx [:db :hardwallet :pin :enter-step] :confirmation))

(defn- pin-match [{:keys [db]}]
  {:db                   (assoc-in db [:hardwallet :pin :status] :validating)
   :utils/dispatch-later [{:ms       3000
                           :dispatch [:hardwallet.callback/on-pin-validated]}]})

(defn- pin-mismatch [fx]
  (assoc-in fx [:db :hardwallet :pin] {:status       :error
                                       :error        :t/pin-mismatch
                                       :original     []
                                       :confirmation []
                                       :enter-step   :original}))

(fx/defn process-pin-input
  [{:keys [db]} number enter-step]
  (let [db' (update-in db [:hardwallet :pin enter-step] conj number)
        numbers-entered (count (get-in db' [:hardwallet :pin enter-step]))]
    (cond-> {:db (assoc-in db' [:hardwallet :pin :status] nil)}
      (and (= enter-step :original)
           (= 6 numbers-entered))
      (proceed-to-pin-confirmation)

      (and (= enter-step :confirmation)
           (= (get-in db' [:hardwallet :pin :original])
              (get-in db' [:hardwallet :pin :confirmation])))
      (pin-match)

      (and (= enter-step :confirmation)
           (= 6 numbers-entered)
           (not= (get-in db' [:hardwallet :pin :original])
                 (get-in db' [:hardwallet :pin :confirmation])))
      (pin-mismatch))))

(fx/defn load-loading-keys-screen
  [{:keys [db]}]
  {:db                               (assoc-in db [:hardwallet :setup-step] :loading-keys)
   :hardwallet/generate-and-load-key nil})

(fx/defn load-generating-mnemonic-screen
  [{:keys [db]}]
  {:db       (assoc-in db [:hardwallet :setup-step] :generating-mnemonic)
   :dispatch [:hardwallet/generate-mnemonic]})

(fx/defn generate-mnemonic
  [cofx]
  (let [{:keys [pairing]} (get-in cofx [:db :hardwallet :secrets])]
    {:hardwallet/generate-mnemonic {:pairing pairing}}))

(fx/defn on-card-connected
  [{:keys [db] :as cofx} data]
  (log/debug "[hardwallet] card connected " data)
  (let [step (get-in db [:hardwallet :return-to-step] :begin)]
    (fx/merge cofx
              {:db                              (-> db
                                                    (assoc-in [:hardwallet :setup-step] step)
                                                    (assoc-in [:hardwallet :card-connected?] true))
               :hardwallet/get-application-info nil}
              (navigation/navigate-to-cofx :hardwallet-setup nil))))

(fx/defn on-card-disconnected
  [{:keys [db]} _]
  (log/debug "[hardwallet] card disconnected ")
  {:db (assoc-in db [:hardwallet :card-connected?] false)})

(fx/defn load-preparing-screen
  [{:keys [db]}]
  {:db       (assoc-in db [:hardwallet :setup-step] :preparing)
   :dispatch [:hardwallet/install-applet-and-init-card]})

(fx/defn install-applet-and-init-card
  [{:keys [db]}]
  {:hardwallet/install-applet-and-init-card nil})

(fx/defn on-install-applet-and-init-card-success
  [{:keys [db]} secrets]
  (let [secrets' (js->clj secrets :keywordize-keys true)]
    {:db (-> db
             (assoc-in [:hardwallet :setup-step] :secret-keys)
             (assoc-in [:hardwallet :secrets] secrets'))}))

(fx/defn on-install-applet-and-init-card-error
  [{:keys [db]} error]
  (log/debug "[hardwallet] install applet and init card error: " error)
  {:db (-> db
           (assoc-in [:hardwallet :setup-step] :error)
           (assoc-in [:hardwallet :return-to-step] :begin)
           (assoc-in [:hardwallet :setup-error] error))})

(fx/defn on-pairing-success
  [{:keys [db]} pairing]
  {:db (-> db
           (assoc-in [:hardwallet :setup-step] :card-ready)
           (assoc-in [:hardwallet :secrets :pairing] pairing))})

(fx/defn on-pairing-error
  [{:keys [db]} error]
  (log/debug "[hardwallet] pairing error: " error)
  {:db (-> db
           (assoc-in [:hardwallet :setup-step] :error)
           (assoc-in [:hardwallet :return-to-step] :secret-keys)
           (assoc-in [:hardwallet :setup-error] error))})

(fx/defn on-generate-mnemonic-success
  [{:keys [db]} mnemonic]
  {:db (-> db
           (assoc-in [:hardwallet :setup-step] :recovery-phrase)
           (assoc-in [:hardwallet :secrets :mnemonic] mnemonic))})

(fx/defn on-generate-mnemonic-error
  [{:keys [db]} error]
  (log/debug "[hardwallet] generate mnemonic error: " error)
  {:db (-> db
           (assoc-in [:hardwallet :setup-step] :error)
           (assoc-in [:hardwallet :return-to-step] :card-ready)
           (assoc-in [:hardwallet :setup-error] error))})

(fx/defn on-pin-validated [{:keys [db] :as cofx}]
  (let [pin (get-in db [:hardwallet :pin :original])]
    (fx/merge cofx)))

(fx/defn recovery-phrase-start-confirmation [{:keys [db]}]
  (let [mnemonic (get-in db [:hardwallet :secrets :mnemonic])
        [word1 word2] (shuffle (map-indexed vector (clojure.string/split mnemonic #" ")))
        word1 (zipmap [:idx :word] word1)
        word2 (zipmap [:idx :word] word2)]
    {:db (-> db
             (assoc-in [:hardwallet :setup-step] :recovery-phrase-confirm-word1)
             (assoc-in [:hardwallet :recovery-phrase :step] :word1)
             (assoc-in [:hardwallet :recovery-phrase :confirm-error] nil)
             (assoc-in [:hardwallet :recovery-phrase :input-word] nil)
             (assoc-in [:hardwallet :recovery-phrase :word1] word1)
             (assoc-in [:hardwallet :recovery-phrase :word2] word2))}))

(defn- show-recover-confirmation []
  {:ui/show-confirmation {:title               (i18n/label :t/are-you-sure?)
                          :content             (i18n/label :t/are-you-sure-description)
                          :confirm-button-text (clojure.string/upper-case (i18n/label :t/yes))
                          :cancel-button-text  (i18n/label :t/see-it-again)
                          :on-accept           #(re-frame/dispatch [:hardwallet.ui/recovery-phrase-confirm-pressed])
                          :on-cancel           #(re-frame/dispatch [:hardwallet.ui/recovery-phrase-cancel-pressed])}})

(defn- recovery-phrase-next-word [db]
  {:db (-> db
           (assoc-in [:hardwallet :recovery-phrase :step] :word2)
           (assoc-in [:hardwallet :recovery-phrase :confirm-error] nil)
           (assoc-in [:hardwallet :recovery-phrase :input-word] nil)
           (assoc-in [:hardwallet :setup-step] :recovery-phrase-confirm-word2))})

(fx/defn recovery-phrase-confirm-word
  [{:keys [db]}]
  (let [step (get-in db [:hardwallet :recovery-phrase :step])
        input-word (get-in db [:hardwallet :recovery-phrase :input-word])
        {:keys [word]} (get-in db [:hardwallet :recovery-phrase step])]
    (if (= word input-word)
      (if (= step :word1)
        (recovery-phrase-next-word db)
        (show-recover-confirmation))
      {:db (assoc-in db [:hardwallet :recovery-phrase :confirm-error] (i18n/label :t/wrong-word))})))

(fx/defn generate-and-load-key
  [{:keys [db] :as cofx}]
  (let [{:keys [mnemonic pairing pin]} (get-in db [:hardwallet :secrets])]
    (fx/merge cofx
              {:hardwallet/generate-and-load-key {:mnemonic mnemonic
                                                  :pairing  pairing
                                                  :pin      pin}})))

(fx/defn on-generate-and-load-key-success
  [{:keys [db] :as cofx} data]
  (let [{:keys [whisper-public-key
                whisper-private-key
                whisper-address
                wallet-address
                encryption-public-key]} (js->clj data :keywordize-keys true)
        whisper-public-key' (str "0x" whisper-public-key)
        keycard-instance-uid (get-in db [:hardwallet :application-info :instance-uid])]
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:hardwallet :whisper-public-key] whisper-public-key')
                       (assoc-in [:hardwallet :whisper-private-key] whisper-private-key)
                       (assoc-in [:hardwallet :whisper-address] whisper-address)
                       (assoc-in [:hardwallet :wallet-address] wallet-address)
                       (assoc-in [:hardwallet :encryption-public-key] encryption-public-key))}
              (accounts.create/on-account-created {:pubkey   whisper-public-key'
                                                   :address  wallet-address
                                                   :mnemonic ""
                                                   :keycard-instance-uid keycard-instance-uid}
                                                  encryption-public-key
                                                  true)
              (navigation/navigate-to-cofx :hardwallet-success nil))))

(fx/defn on-generate-and-load-key-error
  [{:keys [db]} error]
  (log/debug "[hardwallet] generate and load key error: " error)
  {:db (-> db
           (assoc-in [:hardwallet :setup-step] :error)
           (assoc-in [:hardwallet :setup-error] error))})
