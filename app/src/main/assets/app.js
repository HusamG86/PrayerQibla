/* PrayerQibla — UI and native bridge integration.
   Prayer-time math is delegated to MainActivity/PrayerCalc when the native
   bridge exists; calcDay() is only a browser fallback. */
(function () {
  "use strict";

  var KAABA = { lat: 21.4225, lng: 39.8262 };
  var CITIES = [
    ["riyadh", "الرياض", "Riyadh", 24.7136, 46.6753],
    ["jeddah", "جدة", "Jeddah", 21.4858, 39.1925],
    ["makkah", "مكة المكرمة", "Makkah", 21.3891, 39.8579],
    ["madinah", "المدينة المنورة", "Madinah", 24.5247, 39.5692],
    ["dammam", "الدمام", "Dammam", 26.4207, 50.0888],
    ["buraydah", "بريدة", "Buraydah", 26.326, 43.975],
    ["abha", "أبها", "Abha", 18.2164, 42.5053],
    ["tabuk", "تبوك", "Tabuk", 28.3838, 36.5662],
    ["taif", "الطائف", "Taif", 21.2703, 40.4158],
    ["hail", "حائل", "Hail", 27.5114, 41.7208],
    ["jazan", "جازان", "Jazan", 16.8892, 42.5611],
    ["najran", "نجران", "Najran", 17.4917, 44.1322],
    ["khobar", "الخبر", "Al Khobar", 26.2172, 50.1971],
    ["yanbu", "ينبع", "Yanbu", 24.0895, 38.0618],
    ["hofuf", "الهفوف", "Hofuf", 25.3648, 49.5878],
    ["khamis", "خميس مشيط", "Khamis Mushait", 18.3, 42.7292],
    ["jubail", "الجبيل", "Jubail", 27.0046, 49.646],
    ["sakaka", "سكاكا", "Sakaka", 29.9697, 40.2064],
    ["arar", "عرعر", "Arar", 30.9753, 41.0381]
  ];

  var LANGUAGE_NAMES = {
    ar: "العربية", en: "English", ur: "اردو",
    fr: "Français", id: "Bahasa Indonesia", tr: "Türkçe"
  };

  var DATE_LOCALES = {
    ar: "ar-SA-u-ca-islamic-umalqura-nu-arab",
    en: "en-GB-u-ca-islamic-umalqura",
    ur: "ur-PK-u-ca-islamic-umalqura-nu-arab",
    fr: "fr-FR-u-ca-islamic-umalqura",
    id: "id-ID-u-ca-islamic-umalqura",
    tr: "tr-TR-u-ca-islamic-umalqura"
  };

  var METHOD_DEFS = {
    UMM_AL_QURA: { fajrAngle: 18.5, ishaAngle: null, ishaInterval: 90 },
    MUSLIM_WORLD_LEAGUE: { fajrAngle: 18, ishaAngle: 17, ishaInterval: null },
    ISNA: { fajrAngle: 15, ishaAngle: 15, ishaInterval: null },
    EGYPTIAN: { fajrAngle: 19.5, ishaAngle: 17.5, ishaInterval: null },
    KARACHI: { fajrAngle: 18, ishaAngle: 18, ishaInterval: null }
  };

  var ASR_DEFS = { STANDARD: 1, HANAFI: 2 };
  var HIGH_LAT_DEFS = ["ANGLE_BASED", "ONE_SEVENTH", "MIDDLE_OF_NIGHT", "NONE"];
  var ALL_PRAYER_MASK = 61;
  var PRAYER_BITS = { 0: 1, 2: 4, 3: 8, 4: 16, 5: 32 };
  var STORE_KEY = "prayerqibla.prefs";
  var RAD = Math.PI / 180;
  var DEG = 180 / Math.PI;

  var nativeBridge = null;
  try { nativeBridge = window.NativeAdhan || null; } catch (e) { nativeBridge = null; }

  var state = {
    lang: "en",
    theme: "",
    cityId: "riyadh",
    lat: CITIES[0][3],
    lng: CITIES[0][4],
    zone: "Asia/Riyadh",
    custom: false,
    name: "",
    method: "UMM_AL_QURA",
    asr: "STANDARD",
    highLat: "ANGLE_BASED",
    timeFormat: "auto",
    mask: 0,
    local: null,
    dateKey: "",
    times: [],
    nextAt: 0,
    nextIndex: -1,
    nextTomorrow: false,
    tomorrowTimes: null,
    monthOpen: false,
    monthCursor: null,
    compassOn: false,
    compassSource: "",
    compassAccuracy: "",
    heading: null,
    smooth: null,
    declination: 0,
    lastHeadingAt: 0,
    gpsBusy: false,
    locationSource: "",
    lastFired: null,
    raf: 0,
    webListening: false,
    compassTimeout: 0
  };

  var detected = "";
  try {
    detected = (navigator.language || "en").slice(0, 2).toLowerCase();
  } catch (e) { detected = "en"; }

  function T_HAS(code) {
    return Object.prototype.hasOwnProperty.call(T, code);
  }

  function tr(key) {
    var pack = T[state.lang] || T.en;
    var value = pack[key];
    if (value === undefined || value === null) value = T.en[key];
    return value;
  }

  var el = {
    title: document.getElementById("title"),
    subtitle: document.getElementById("subtitle"),
    themeBtn: document.getElementById("themeBtn"),
    settingsBtn: document.getElementById("settingsBtn"),
    langSelect: document.getElementById("langSelect"),
    citySelect: document.getElementById("citySelect"),
    gpsBtn: document.getElementById("gpsBtn"),
    nextLabel: document.getElementById("nextLabel"),
    nextName: document.getElementById("nextName"),
    countdown: document.getElementById("countdown"),
    dates: document.getElementById("dates"),
    permCard: document.getElementById("permCard"),
    permTitle: document.getElementById("permTitle"),
    permNotifRow: document.getElementById("permNotifRow"),
    permNotifLabel: document.getElementById("permNotifLabel"),
    permNotifState: document.getElementById("permNotifState"),
    permNotifBtn: document.getElementById("permNotifBtn"),
    permExactRow: document.getElementById("permExactRow"),
    permExactLabel: document.getElementById("permExactLabel"),
    permExactState: document.getElementById("permExactState"),
    permExactBtn: document.getElementById("permExactBtn"),
    permBatteryRow: document.getElementById("permBatteryRow"),
    permBatteryLabel: document.getElementById("permBatteryLabel"),
    permBatteryState: document.getElementById("permBatteryState"),
    permBatteryBtn: document.getElementById("permBatteryBtn"),
    prayerList: document.getElementById("prayerList"),
    alertBtn: document.getElementById("alertBtn"),
    monthBtn: document.getElementById("monthBtn"),
    month: document.getElementById("month"),
    monthLabel: document.getElementById("monthLabel"),
    monthTable: document.getElementById("monthTable"),
    monthPrev: document.getElementById("monthPrev"),
    monthNext: document.getElementById("monthNext"),
    qiblaTitle: document.getElementById("qiblaTitle"),
    qiblaDeg: document.getElementById("qiblaDeg"),
    qiblaSvg: document.getElementById("qiblaSvg"),
    qiblaNote: document.getElementById("qiblaNote"),
    compassState: document.getElementById("compassState"),
    compassBtn: document.getElementById("compassBtn"),
    dial: document.getElementById("dial"),
    needle: document.getElementById("needle"),
    settingsSheet: document.getElementById("settingsSheet"),
    settingsBackdrop: document.getElementById("settingsBackdrop"),
    settingsTitle: document.getElementById("settingsTitle"),
    settingsClose: document.getElementById("settingsClose"),
    methodLabel: document.getElementById("methodLabel"),
    methodSelect: document.getElementById("methodSelect"),
    asrLabel: document.getElementById("asrLabel"),
    asrSelect: document.getElementById("asrSelect"),
    highLatLabel: document.getElementById("highLatLabel"),
    highLatSelect: document.getElementById("highLatSelect"),
    timeFormatLabel: document.getElementById("timeFormatLabel"),
    timeFormatSelect: document.getElementById("timeFormatSelect"),
    settingsNote: document.getElementById("settingsNote"),
    toast: document.getElementById("toast"),
    themeColor: document.getElementById("themeColor")
  };

  var T = {
    en: {
      title: "Prayer Times & Qibla",
      gps: "My location",
      locating: "Locating…",
      next: "Next",
      qt: "Qibla direction",
      from: "from North",
      compass: "Enable compass",
      stopCompass: "Stop compass",
      qn: "Enable the compass and hold your device flat. The needle points to the Qibla.",
      names: ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"],
      gpsErr: "Couldn’t get your location",
      gpsDenied: "Location permission is denied",
      locUpdated: "Location updated",
      custom: "My location",
      am: "AM",
      pm: "PM",
      method: "Umm al-Qura method",
      enableAll: "🔔 Enable all alerts",
      disableAll: "🔕 Turn off all alerts",
      prayerOn: "Turn off ",
      prayerOff: "Enable ",
      mBtn: "📅 Monthly timetable",
      mHide: "Hide timetable",
      hj: "Hijri",
      gr: "Date",
      time: "It’s time for ",
      settings: "Settings",
      methodLabel: "Calculation method",
      asrLabel: "Asr calculation",
      highLatLabel: "High-latitude rule",
      timeFormatLabel: "Time format",
      formatAuto: "Auto",
      format12: "12-hour",
      format24: "24-hour",
      close: "Close",
      theme: "Toggle theme",
      language: "Language",
      city: "City",
      compassOn: "Compass active",
      compassWaiting: "Waiting for compass…",
      compassUnavailable: "Compass unavailable",
      accuracyHigh: "high accuracy",
      accuracyMedium: "medium accuracy",
      accuracyLow: "low accuracy",
      accuracyUnreliable: "unreliable",
      permTitle: "Alerts need attention",
      permNotif: "Notifications",
      permExact: "Exact alarms",
      permBattery: "Battery optimization",
      permAllowed: "Allowed",
      permOff: "Off",
      permFix: "Fix",
      batteryRestricted: "Restricted",
      settingsNote: "Times are calculated on your device. Alerts work with the app closed.",
      noCompassToast: "No compass sensor was found on this device.",
      monthPrev: "Previous month",
      monthNext: "Next month",
      alertOnToast: "Adhan alerts enabled",
      alertOffToast: "Adhan alerts disabled",
      permissionDenied: "Notifications are disabled",
      methods: {
        UMM_AL_QURA: "Umm al-Qura",
        MUSLIM_WORLD_LEAGUE: "Muslim World League",
        ISNA: "ISNA (North America)",
        EGYPTIAN: "Egyptian Authority",
        KARACHI: "University of Karachi"
      },
      asrMethods: {
        STANDARD: "Standard (Shafi'i, Maliki, Hanbali)",
        HANAFI: "Hanafi"
      },
      highLatRules: {
        ANGLE_BASED: "Angle-based",
        ONE_SEVENTH: "One-seventh of night",
        MIDDLE_OF_NIGHT: "Middle of night",
        NONE: "None"
      }
    },
    ar: {
      title: "مواقيت الصلاة والقبلة",
      gps: "موقعي",
      locating: "جارٍ تحديد الموقع…",
      next: "التالي",
      qt: "اتجاه القبلة",
      from: "من الشمال",
      compass: "تفعيل البوصلة",
      stopCompass: "إيقاف البوصلة",
      qn: "فعّل البوصلة وأمسك الجهاز بشكل أفقي. تشير الإبرة إلى القبلة.",
      names: ["الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"],
      gpsErr: "تعذّر تحديد الموقع",
      gpsDenied: "تم رفض إذن الموقع",
      locUpdated: "تم تحديث الموقع",
      custom: "موقعي الحالي",
      am: "ص",
      pm: "م",
      method: "طريقة أم القرى",
      enableAll: "🔔 تفعيل كل التنبيهات",
      disableAll: "🔕 إيقاف كل التنبيهات",
      prayerOn: "إيقاف تنبيه ",
      prayerOff: "تفعيل تنبيه ",
      mBtn: "📅 الجدول الشهري",
      mHide: "إخفاء الجدول",
      hj: "هجري",
      gr: "ميلادي",
      time: "حان وقت صلاة ",
      settings: "الإعدادات",
      methodLabel: "طريقة الحساب",
      asrLabel: "حساب العصر",
      highLatLabel: "قاعدة العروض العالية",
      timeFormatLabel: "تنسيق الوقت",
      formatAuto: "تلقائي",
      format12: "12 ساعة",
      format24: "24 ساعة",
      close: "إغلاق",
      theme: "تغيير المظهر",
      language: "اللغة",
      city: "المدينة",
      compassOn: "البوصلة مفعّلة",
      compassWaiting: "جارٍ انتظار البوصلة…",
      compassUnavailable: "البوصلة غير متاحة",
      accuracyHigh: "دقة عالية",
      accuracyMedium: "دقة متوسطة",
      accuracyLow: "دقة منخفضة",
      accuracyUnreliable: "غير موثوقة",
      permTitle: "التنبيهات تحتاج إلى ضبط",
      permNotif: "الإشعارات",
      permExact: "المنبهات الدقيقة",
      permBattery: "تحسين البطارية",
      permAllowed: "مسموح",
      permOff: "متوقف",
      permFix: "إصلاح",
      batteryRestricted: "مقيّد",
      settingsNote: "تُحسب المواقيت على جهازك، وتعمل التنبيهات والتطبيق مغلق.",
      noCompassToast: "لم يتم العثور على حساس بوصلة في هذا الجهاز.",
      monthPrev: "الشهر السابق",
      monthNext: "الشهر التالي",
      alertOnToast: "تم تفعيل تنبيهات الأذان",
      alertOffToast: "تم إيقاف تنبيهات الأذان",
      permissionDenied: "الإشعارات معطّلة",
      methods: {
        UMM_AL_QURA: "أم القرى",
        MUSLIM_WORLD_LEAGUE: "رابطة العالم الإسلامي",
        ISNA: "الجمعية الإسلامية بأمريكا الشمالية",
        EGYPTIAN: "الهيئة المصرية العامة للمساحة",
        KARACHI: "جامعة كراتشي"
      },
      asrMethods: {
        STANDARD: "الجمهور (الشافعي، المالكي، الحنبلي)",
        HANAFI: "الحنفي"
      },
      highLatRules: {
        ANGLE_BASED: "حسب الزاوية",
        ONE_SEVENTH: "سُبع الليل",
        MIDDLE_OF_NIGHT: "منتصف الليل",
        NONE: "بدون"
      }
    },
    ur: {
      title: "نماز کے اوقات اور قبلہ",
      gps: "میرا مقام",
      locating: "مقام تلاش کیا جا رہا ہے…",
      next: "اگلا",
      qt: "قبلہ رخ",
      from: "شمال سے",
      compass: "کمپاس فعال کریں",
      stopCompass: "کمپاس بند کریں",
      qn: "کمپاس فعال کریں اور فون کو ہموار رکھیں۔ سوئی قبلہ کی طرف اشارہ کرتی ہے۔",
      names: ["فجر", "طلوع آفتاب", "ظہر", "عصر", "مغرب", "عشاء"],
      gpsErr: "مقام حاصل نہیں ہو سکا",
      gpsDenied: "مقام کی اجازت نہیں دی گئی",
      locUpdated: "مقام اپ ڈیٹ ہو گیا",
      custom: "میرا مقام",
      am: "صبح",
      pm: "شام",
      method: "طریقۂ ام القری",
      enableAll: "🔔 تمام اذانیں فعال کریں",
      disableAll: "🔕 تمام اذانیں بند کریں",
      prayerOn: "اذان بند کریں: ",
      prayerOff: "اذان فعال کریں: ",
      mBtn: "📅 ماہانہ جدول",
      mHide: "جدول چھپائیں",
      hj: "ہجری",
      gr: "عیسوی",
      time: "نماز کا وقت ہے: ",
      settings: "ترتیبات",
      methodLabel: "حساب کا طریقہ",
      asrLabel: "عصر کا حساب",
      highLatLabel: "بلند عرض کا قاعدہ",
      timeFormatLabel: "وقت کی شکل",
      formatAuto: "خودکار",
      format12: "12 گھنٹے",
      format24: "24 گھنٹے",
      close: "بند کریں",
      theme: "ظاہری شکل بدلیں",
      language: "زبان",
      city: "شہر",
      compassOn: "کمپاس فعال ہے",
      compassWaiting: "کمپاس کا انتظار…",
      compassUnavailable: "کمپاس دستیاب نہیں",
      accuracyHigh: "زیادہ درستگی",
      accuracyMedium: "درمیانی درستگی",
      accuracyLow: "کم درستگی",
      accuracyUnreliable: "ناقابلِ اعتماد",
      permTitle: "اطلاعات کو توجہ درکار ہے",
      permNotif: "اطلاعات",
      permExact: "درست المنبہات",
      permBattery: "بیٹری آپٹیمائزیشن",
      permAllowed: "اجازت ہے",
      permOff: "بند",
      permFix: "درست کریں",
      batteryRestricted: "محدود",
      settingsNote: "اوقات آپ کے آلے پر حساب ہوتے ہیں۔ اذانیں ایپ بند ہونے پر بھی کام کرتی ہیں۔",
      noCompassToast: "اس آلے میں کمپاس سینسر نہیں ملا۔",
      monthPrev: "پچھلا مہینہ",
      monthNext: "اگلا مہینہ",
      alertOnToast: "اذان کے اطلاعات فعال ہو گئے",
      alertOffToast: "اذان کے اطلاعات بند ہو گئے",
      permissionDenied: "اطلاعات بند ہیں",
      methods: {
        UMM_AL_QURA: "ام القری",
        MUSLIM_WORLD_LEAGUE: "مسلم ورلڈ لیگ",
        ISNA: "اسلامک سوسائٹی آف نارتھ امریکہ",
        EGYPTIAN: "مصری سروے اتھارٹی",
        KARACHI: "کراچی یونیورسٹی"
      },
      asrMethods: {
        STANDARD: "جمہور (شافعی، مالکی، حنبلی)",
        HANAFI: "حنفی"
      },
      highLatRules: {
        ANGLE_BASED: "زاویے کے مطابق",
        ONE_SEVENTH: "رات کا ساتواں حصہ",
        MIDDLE_OF_NIGHT: "آدھی رات",
        NONE: "کوئی نہیں"
      }
    },
    fr: {
      title: "Horaires de prière et Qibla",
      gps: "Ma position",
      locating: "Localisation…",
      next: "Suivant",
      qt: "Direction de la Qibla",
      from: "depuis le nord",
      compass: "Activer la boussole",
      stopCompass: "Arrêter la boussole",
      qn: "Activez la boussole et tenez le téléphone à plat. L’aiguille indique la Qibla.",
      names: ["Fajr", "Lever du soleil", "Dhuhr", "Asr", "Maghrib", "Isha"],
      gpsErr: "Position introuvable",
      gpsDenied: "L’autorisation de localisation est refusée",
      locUpdated: "Position mise à jour",
      custom: "Ma position",
      am: "AM",
      pm: "PM",
      method: "Méthode Umm al-Qura",
      enableAll: "🔔 Activer toutes les alertes",
      disableAll: "🔕 Désactiver toutes les alertes",
      prayerOn: "Désactiver l’alerte ",
      prayerOff: "Activer l’alerte ",
      mBtn: "📅 Calendrier mensuel",
      mHide: "Masquer le calendrier",
      hj: "Hégirien",
      gr: "Grégorien",
      time: "C’est l’heure de ",
      settings: "Paramètres",
      methodLabel: "Méthode de calcul",
      asrLabel: "Calcul de l’Asr",
      highLatLabel: "Règle des hautes latitudes",
      timeFormatLabel: "Format de l’heure",
      formatAuto: "Auto",
      format12: "12 heures",
      format24: "24 heures",
      close: "Fermer",
      theme: "Changer de thème",
      language: "Langue",
      city: "Ville",
      compassOn: "Boussole active",
      compassWaiting: "En attente de la boussole…",
      compassUnavailable: "Boussole indisponible",
      accuracyHigh: "précision élevée",
      accuracyMedium: "précision moyenne",
      accuracyLow: "faible précision",
      accuracyUnreliable: "non fiable",
      permTitle: "Les alertes nécessitent votre attention",
      permNotif: "Notifications",
      permExact: "Alarmes exactes",
      permBattery: "Optimisation de la batterie",
      permAllowed: "Autorisé",
      permOff: "Désactivé",
      permFix: "Corriger",
      batteryRestricted: "Restreint",
      settingsNote: "Les horaires sont calculés sur votre appareil. Les alertes fonctionnent même si l’application est fermée.",
      noCompassToast: "Aucun capteur de boussole n’a été trouvé sur cet appareil.",
      monthPrev: "Mois précédent",
      monthNext: "Mois suivant",
      alertOnToast: "Alertes de prière activées",
      alertOffToast: "Alertes de prière désactivées",
      permissionDenied: "Les notifications sont désactivées",
      methods: {
        UMM_AL_QURA: "Umm al-Qura",
        MUSLIM_WORLD_LEAGUE: "Ligue islamique mondiale",
        ISNA: "ISNA (Amérique du Nord)",
        EGYPTIAN: "Autorité égyptienne",
        KARACHI: "Université de Karachi"
      },
      asrMethods: {
        STANDARD: "Standard (chaféite, malikite, hanbalite)",
        HANAFI: "Hanafite"
      },
      highLatRules: {
        ANGLE_BASED: "Selon l’angle",
        ONE_SEVENTH: "Un septième de la nuit",
        MIDDLE_OF_NIGHT: "Milieu de la nuit",
        NONE: "Aucune"
      }
    },
    id: {
      title: "Waktu Salat & Kiblat",
      gps: "Lokasi saya",
      locating: "Mencari lokasi…",
      next: "Berikutnya",
      qt: "Arah kiblat",
      from: "dari utara",
      compass: "Aktifkan kompas",
      stopCompass: "Matikan kompas",
      qn: "Aktifkan kompas dan pegang ponsel mendatar. Jarum menunjuk ke arah kiblat.",
      names: ["Subuh", "Terbit", "Zuhur", "Asar", "Magrib", "Isya"],
      gpsErr: "Lokasi tidak ditemukan",
      gpsDenied: "Izin lokasi ditolak",
      locUpdated: "Lokasi diperbarui",
      custom: "Lokasi saya",
      am: "AM",
      pm: "PM",
      method: "Metode Umm al-Qura",
      enableAll: "🔔 Aktifkan semua pengingat",
      disableAll: "🔕 Matikan semua pengingat",
      prayerOn: "Matikan pengingat ",
      prayerOff: "Aktifkan pengingat ",
      mBtn: "📅 Jadwal bulanan",
      mHide: "Sembunyikan jadwal",
      hj: "Hijriah",
      gr: "Masehi",
      time: "Waktu salat ",
      settings: "Pengaturan",
      methodLabel: "Metode perhitungan",
      asrLabel: "Perhitungan Asar",
      highLatLabel: "Aturan lintang tinggi",
      timeFormatLabel: "Format waktu",
      formatAuto: "Otomatis",
      format12: "12 jam",
      format24: "24 jam",
      close: "Tutup",
      theme: "Ubah tema",
      language: "Bahasa",
      city: "Kota",
      compassOn: "Kompas aktif",
      compassWaiting: "Menunggu kompas…",
      compassUnavailable: "Kompas tidak tersedia",
      accuracyHigh: "akurasi tinggi",
      accuracyMedium: "akurasi sedang",
      accuracyLow: "akurasi rendah",
      accuracyUnreliable: "tidak akurat",
      permTitle: "Pengingat perlu diperiksa",
      permNotif: "Notifikasi",
      permExact: "Alarm tepat",
      permBattery: "Optimasi baterai",
      permAllowed: "Diizinkan",
      permOff: "Mati",
      permFix: "Perbaiki",
      batteryRestricted: "Dibatasi",
      settingsNote: "Waktu dihitung di perangkat Anda. Pengingat tetap bekerja saat aplikasi ditutup.",
      noCompassToast: "Sensor kompas tidak ditemukan di perangkat ini.",
      monthPrev: "Bulan sebelumnya",
      monthNext: "Bulan berikutnya",
      alertOnToast: "Pengingat azan diaktifkan",
      alertOffToast: "Pengingat azan dimatikan",
      permissionDenied: "Notifikasi dinonaktifkan",
      methods: {
        UMM_AL_QURA: "Umm al-Qura",
        MUSLIM_WORLD_LEAGUE: "Liga Muslim Dunia",
        ISNA: "ISNA (Amerika Utara)",
        EGYPTIAN: "Otoritas Mesir",
        KARACHI: "Universitas Karachi"
      },
      asrMethods: {
        STANDARD: "Standar (Syafi'i, Maliki, Hanbali)",
        HANAFI: "Hanafi"
      },
      highLatRules: {
        ANGLE_BASED: "Berdasarkan sudut",
        ONE_SEVENTH: "Sepertujuh malam",
        MIDDLE_OF_NIGHT: "Tengah malam",
        NONE: "Tidak ada"
      }
    },
    tr: {
      title: "Namaz Vakitleri ve Kıble",
      gps: "Konumum",
      locating: "Konum alınıyor…",
      next: "Sıradaki",
      qt: "Kıble yönü",
      from: "kuzeyden",
      compass: "Pusulayı etkinleştir",
      stopCompass: "Pusulayı kapat",
      qn: "Pusulayı açın ve telefonu düz tutun. İbre kıbleyi gösterir.",
      names: ["İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"],
      gpsErr: "Konum alınamadı",
      gpsDenied: "Konum izni verilmedi",
      locUpdated: "Konum güncellendi",
      custom: "Konumum",
      am: "ÖÖ",
      pm: "ÖS",
      method: "Ümmü’l-Kurâ yöntemi",
      enableAll: "🔔 Tüm bildirimleri aç",
      disableAll: "🔕 Tüm bildirimleri kapat",
      prayerOn: "Bildirimleri kapat: ",
      prayerOff: "Bildirimi aç: ",
      mBtn: "📅 Aylık çizelge",
      mHide: "Çizelgeyi gizle",
      hj: "Hicrî",
      gr: "Miladi",
      time: "Namaz vakti: ",
      settings: "Ayarlar",
      methodLabel: "Hesaplama yöntemi",
      asrLabel: "İkindi hesabı",
      highLatLabel: "Yüksek enlem kuralı",
      timeFormatLabel: "Saat biçimi",
      formatAuto: "Otomatik",
      format12: "12 saat",
      format24: "24 saat",
      close: "Kapat",
      theme: "Temayı değiştir",
      language: "Dil",
      city: "Şehir",
      compassOn: "Pusula etkin",
      compassWaiting: "Pusula bekleniyor…",
      compassUnavailable: "Pusula kullanılamıyor",
      accuracyHigh: "yüksek doğruluk",
      accuracyMedium: "orta doğruluk",
      accuracyLow: "düşük doğruluk",
      accuracyUnreliable: "güvenilmez",
      permTitle: "Bildirimler dikkat gerektiriyor",
      permNotif: "Bildirimler",
      permExact: "Kesin alarmlar",
      permBattery: "Pil optimizasyonu",
      permAllowed: "İzinli",
      permOff: "Kapalı",
      permFix: "Düzelt",
      batteryRestricted: "Kısıtlı",
      settingsNote: "Vakitler cihazınızda hesaplanır. Bildirimler uygulama kapalıyken de çalışır.",
      noCompassToast: "Bu cihazda pusula sensörü bulunamadı.",
      monthPrev: "Önceki ay",
      monthNext: "Sonraki ay",
      alertOnToast: "Ezan bildirimleri açıldı",
      alertOffToast: "Ezan bildirimleri kapatıldı",
      permissionDenied: "Bildirimler kapalı",
      methods: {
        UMM_AL_QURA: "Ümmü’l-Kurâ",
        MUSLIM_WORLD_LEAGUE: "Dünya Müslümanlar Birliği",
        ISNA: "ISNA (Kuzey Amerika)",
        EGYPTIAN: "Mısır Kurumu",
        KARACHI: "Karaçi Üniversitesi"
      },
      asrMethods: {
        STANDARD: "Standart (Şafii, Maliki, Hanbeli)",
        HANAFI: "Hanefi"
      },
      highLatRules: {
        ANGLE_BASED: "Açıya göre",
        ONE_SEVENTH: "Gecenin yedide biri",
        MIDDLE_OF_NIGHT: "Gece ortası",
        NONE: "Yok"
      }
    }
  };

  // ---------------------------------------------------------------- utilities

  function pad2(n) {
    return (n < 10 ? "0" : "") + n;
  }

  function digits(value) {
    var map = state.lang === "ar" ? "٠١٢٣٤٥٦٧٨٩"
      : state.lang === "ur" ? "۰۱۲۳۴۵۶۷۸۹" : null;
    if (!map) return String(value);
    return String(value).replace(/\d/g, function (d) { return map.charAt(+d); });
  }

  function normalize(value, base) {
    value = value - base * Math.floor(value / base);
    return value < 0 ? value + base : value;
  }

  function isFiniteNumber(value) {
    return typeof value === "number" && isFinite(value);
  }

  function findCity(id) {
    for (var i = 0; i < CITIES.length; i++) {
      if (CITIES[i][0] === id) return CITIES[i];
    }
    return null;
  }

  function detectZone() {
    try {
      var zone = new Intl.DateTimeFormat().resolvedOptions().timeZone;
      return zone || "UTC";
    } catch (e) {
      return "UTC";
    }
  }

  function gregLocale() {
    if (state.lang === "ar") return "ar-SA-u-ca-gregory-nu-arab";
    if (state.lang === "ur") return "ur-PK-u-ca-gregory-nu-arab";
    if (state.lang === "fr") return "fr-FR";
    if (state.lang === "id") return "id-ID";
    if (state.lang === "tr") return "tr-TR";
    return "en-GB";
  }

  function toast(message) {
    if (!el.toast) return;
    el.toast.textContent = message;
    el.toast.hidden = false;
    if (state.toastTimer) clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(function () {
      el.toast.hidden = true;
    }, 4200);
  }

  // ---------------------------------------------------------------- prefs

  function loadLocalPrefs() {
    var p = {};
    try {
      p = JSON.parse(localStorage.getItem(STORE_KEY) || "{}") || {};
    } catch (e) { p = {}; }

    if (T_HAS(p.lang)) state.lang = p.lang;
    if (p.theme === "light" || p.theme === "dark") state.theme = p.theme;

    var city = p.city ? findCity(p.city) : null;
    if (city) {
      state.cityId = city[0];
      state.lat = city[3];
      state.lng = city[4];
      state.zone = "Asia/Riyadh";
      state.custom = false;
      state.name = "";
    } else if (p.custom && isFiniteNumber(p.lat) && isFiniteNumber(p.lng)) {
      state.lat = p.lat;
      state.lng = p.lng;
      state.custom = true;
      state.name = typeof p.name === "string" ? p.name : "";
      state.cityId = "";
      if (typeof p.zone === "string" && p.zone) {
        state.zone = p.zone;
      } else {
        // Upgrade old preferences that stored only a fixed UTC offset.
        state.zone = detectZone();
      }
    }

    if (typeof p.method === "string" && METHOD_DEFS[p.method]) state.method = p.method;
    if (typeof p.asr === "string" && ASR_DEFS[p.asr]) state.asr = p.asr;
    if (typeof p.highLat === "string" && HIGH_LAT_DEFS.indexOf(p.highLat) >= 0) state.highLat = p.highLat;
    if (p.timeFormat === "auto" || p.timeFormat === "12" || p.timeFormat === "24") {
      state.timeFormat = p.timeFormat;
    }

    if (typeof p.prayerMask === "number" && isFinite(p.prayerMask)) {
      state.mask = p.prayerMask & ALL_PRAYER_MASK;
    } else if (typeof p.alertOn === "boolean") {
      state.mask = p.alertOn ? ALL_PRAYER_MASK : 0;
    }
  }

  function loadNativeConfig() {
    if (!nativeBridge) return;
    var c = null;
    try {
      c = JSON.parse(nativeBridge.getConfig());
    } catch (e) {
      return;
    }
    if (!c) return;

    // The native store wins once it has been written at least once. This lets
    // an upgraded install migrate the old localStorage values on first launch.
    var saved = c.saved === true ||
      (typeof c.lat === "number" && typeof c.lng === "number");
    if (!saved) return;

    if (T_HAS(c.lang)) state.lang = c.lang;
    if (typeof c.mask === "number" && isFinite(c.mask)) state.mask = c.mask & ALL_PRAYER_MASK;

    if (typeof c.lat === "number" && typeof c.lng === "number" &&
        c.lat >= -90 && c.lat <= 90 && c.lng >= -180 && c.lng <= 180) {
      state.lat = c.lat;
      state.lng = c.lng;
      state.custom = !!c.custom;
      if (state.custom) {
        state.cityId = "";
        state.name = typeof c.name === "string" ? c.name : "";
      } else {
        var city = findCity(c.city);
        state.cityId = city ? city[0] : "riyadh";
        state.name = "";
      }
    }

    if (typeof c.zone === "string" && c.zone) state.zone = c.zone;
    if (typeof c.method === "string" && METHOD_DEFS[c.method]) state.method = c.method;
    if (typeof c.asr === "string" && ASR_DEFS[c.asr]) state.asr = c.asr;
    if (typeof c.highLat === "string" && HIGH_LAT_DEFS.indexOf(c.highLat) >= 0) {
      state.highLat = c.highLat;
    }
  }

  function saveLocal() {
    var tz = 0;
    try { tz = zoneOffsetHours(new Date(), state.zone); } catch (e) { tz = 0; }
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify({
        lang: state.lang,
        theme: state.theme,
        city: state.custom ? "" : state.cityId,
        custom: state.custom,
        name: state.name || "",
        lat: state.lat,
        lng: state.lng,
        zone: state.zone,
        tz: tz,
        alertOn: state.mask !== 0,
        prayerMask: state.mask,
        method: state.method,
        asr: state.asr,
        highLat: state.highLat,
        timeFormat: state.timeFormat
      }));
    } catch (e) { /* private browsing / storage full */ }
  }

  function syncNative() {
    if (!nativeBridge) return;
    try {
      nativeBridge.sync(JSON.stringify({
        mask: state.mask,
        lang: state.lang,
        lat: state.lat,
        lng: state.lng,
        zone: state.zone,
        method: state.method,
        asr: state.asr,
        highLat: state.highLat,
        custom: state.custom,
        city: state.custom ? "" : state.cityId,
        name: state.name || ""
      }));
    } catch (e) { /* bridge unavailable */ }
  }

  // ---------------------------------------------------------------- time zones

  var zoneFormatters = {};

  function partsFormatter(zone) {
    var key = "p|" + zone;
    if (!zoneFormatters[key]) {
      zoneFormatters[key] = new Intl.DateTimeFormat("en-US-u-ca-gregory-nu-latn", {
        timeZone: zone,
        hourCycle: "h23",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
      });
    }
    return zoneFormatters[key];
  }

  function offsetFormatter(zone) {
    var key = "o|" + zone;
    if (!zoneFormatters[key]) {
      zoneFormatters[key] = new Intl.DateTimeFormat("en-US", {
        timeZone: zone,
        hourCycle: "h23",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
      });
    }
    return zoneFormatters[key];
  }

  function partMap(format, date) {
    var out = {};
    var parts = format.formatToParts(date);
    for (var i = 0; i < parts.length; i++) out[parts[i].type] = parts[i].value;
    return out;
  }

  function localParts(date) {
    date = date || new Date();
    try {
      var p = partMap(partsFormatter(state.zone), date);
      var y = +p.year, m = +p.month, d = +p.day;
      var hh = +p.hour, mm = +p.minute, ss = +p.second;
      return {
        y: y, m: m, d: d,
        hour: hh, minute: mm, second: ss,
        hourFloat: hh + mm / 60 + ss / 3600,
        epochDay: Math.floor(Date.UTC(y, m - 1, d) / 86400000)
      };
    } catch (e) {
      return {
        y: date.getFullYear(), m: date.getMonth() + 1, d: date.getDate(),
        hour: date.getHours(), minute: date.getMinutes(), second: date.getSeconds(),
        hourFloat: date.getHours() + date.getMinutes() / 60 + date.getSeconds() / 3600,
        epochDay: Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000)
      };
    }
  }

  /** UTC offset in hours for an IANA zone at a particular instant. */
  function zoneOffsetHours(date, zone) {
    if (!zone) return -date.getTimezoneOffset() / 60;
    try {
      var p = partMap(offsetFormatter(zone), date);
      var asUtc = Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute, +p.second);
      // The formatter only resolves whole seconds; drop the input's milliseconds
      // so the offset is not a few seconds off.
      var wholeSeconds = Math.floor(date.getTime() / 1000) * 1000;
      return (asUtc - wholeSeconds) / 3600000;
    } catch (e) {
      return -date.getTimezoneOffset() / 60;
    }
  }

  /** Converts a local wall-clock hour on a date to an absolute epoch millis value. */
  function localHoursToMillis(y, m, d, hours, zone) {
    var localMs = Date.UTC(y, m - 1, d, 0, 0, 0) + hours * 3600000;
    var guess = localMs;
    for (var i = 0; i < 3; i++) {
      var offset = zoneOffsetHours(new Date(guess), zone);
      guess = localMs - offset * 3600000;
    }
    return Math.round(guess);
  }

  /** Converts a UTC hour on a date to wall-clock hours in the selected zone. */
  function utcHourToLocal(y, m, d, utcHours) {
    var ms = Date.UTC(y, m - 1, d) + utcHours * 3600000;
    try {
      var p = partMap(partsFormatter(state.zone), new Date(ms));
      return (+p.hour) + (+p.minute) / 60 + (+p.second) / 3600;
    } catch (e) {
      return utcHours + zoneOffsetHours(new Date(ms), state.zone);
    }
  }

  function addDays(y, m, d, count) {
    var date = new Date(Date.UTC(y, m - 1, d + count));
    return {
      y: date.getUTCFullYear(),
      m: date.getUTCMonth() + 1,
      d: date.getUTCDate()
    };
  }

  // ---------------------------------------------------------------- prayer math

  function halfDayAt(angle, declination, latitude) {
    var c = (-Math.sin(angle * RAD) - Math.sin(declination * RAD) * Math.sin(latitude * RAD)) /
      (Math.cos(declination * RAD) * Math.cos(latitude * RAD));
    if (!isFinite(c) || c < -1 || c > 1) return null;
    return Math.acos(c) * DEG / 15;
  }

  function highLatTime(noon, sunrise, sunset, angle, isFajr, rule) {
    if (rule === "NONE") return isFajr ? noon - 12 : noon + 12;
    var dayLength = Math.min(24, Math.max(0, sunset - sunrise));
    var nightLength = 24 - dayLength;
    var portion = rule === "ONE_SEVENTH" ? 1 / 7
      : rule === "MIDDLE_OF_NIGHT" ? 0.5
        : Math.min(1, Math.max(0, angle / 60));
    return isFajr ? sunrise - nightLength * portion : sunset + nightLength * portion;
  }

  var ramadanFormatter = null;

  function isRamadan(date) {
    try {
      if (!ramadanFormatter) {
        ramadanFormatter = new Intl.DateTimeFormat("en-US-u-ca-islamic-umalqura", {
          month: "numeric",
          timeZone: "UTC"
        });
      }
      var p = partMap(ramadanFormatter, date);
      return +p.month === 9;
    } catch (e) {
      return false;
    }
  }

  /** Browser fallback. Native MainActivity/PrayerCalc is authoritative in the app. */
  function calcDay(y, m, d) {
    var dateUtc = new Date(Date.UTC(y, m - 1, d, 12));
    var lat = state.lat;
    var lng = state.lng;
    var jd = Date.UTC(y, m - 1, d, 12) / 86400000 + 2440587.5 - 2451545.0;
    var g = normalize(357.529 + 0.98560028 * jd, 360);
    var q = normalize(280.459 + 0.98564736 * jd, 360);
    var L = normalize(q + 1.915 * Math.sin(g * RAD) + 0.02 * Math.sin(2 * g * RAD), 360);
    var e = 23.439 - 0.00000036 * jd;
    var ra = normalize(Math.atan2(Math.cos(e * RAD) * Math.sin(L * RAD), Math.cos(L * RAD)) * DEG / 15, 24);
    var dec = Math.asin(Math.sin(e * RAD) * Math.sin(L * RAD)) * DEG;
    // Compute in UTC, then convert each event separately so DST transitions are handled.
    var noon = 12 - lng / 15 - (q / 15 - ra);

    var sunriseHalf = halfDayAt(0.833, dec, lat);
    var sunrise, sunset;
    if (sunriseHalf === null) {
      sunrise = noon - 6;
      sunset = noon + 6;
    } else {
      sunrise = noon - sunriseHalf;
      sunset = noon + sunriseHalf;
    }

    var method = METHOD_DEFS[state.method] || METHOD_DEFS.UMM_AL_QURA;
    var fajrHalf = halfDayAt(method.fajrAngle, dec, lat);
    var fajr = fajrHalf === null
      ? highLatTime(noon, sunrise, sunset, method.fajrAngle, true, state.highLat)
      : noon - fajrHalf;

    var maghrib = sunset;
    var isha;
    if (method.ishaAngle !== null) {
      var ishaHalf = halfDayAt(method.ishaAngle, dec, lat);
      isha = ishaHalf === null
        ? highLatTime(noon, sunrise, sunset, method.ishaAngle, false, state.highLat)
        : noon + ishaHalf;
    } else {
      var interval = method.ishaInterval + (isRamadan(dateUtc) ? 30 : 0);
      isha = maghrib + interval / 60;
    }

    var shadow = (ASR_DEFS[state.asr] || 1) + Math.tan(Math.abs(lat - dec) * RAD);
    var asrAltitude = Math.atan(1 / shadow);
    var asrC = (Math.sin(asrAltitude) - Math.sin(dec * RAD) * Math.sin(lat * RAD)) /
      (Math.cos(dec * RAD) * Math.cos(lat * RAD));
    var asrTime = Math.acos(Math.max(-1, Math.min(1, asrC))) * DEG / 15;

    return [fajr, sunrise, noon, noon + asrTime, maghrib, isha].map(function (utcHour) {
      return utcHourToLocal(y, m, d, utcHour);
    });
  }

  function validTimes(values) {
    if (!values || values.length < 6) return null;
    for (var i = 0; i < 6; i++) {
      if (!isFiniteNumber(values[i])) return null;
    }
    return values.slice(0, 6);
  }

  function dayTimesFor(y, m, d) {
    if (nativeBridge) {
      try {
        var epochDay = Math.floor(Date.UTC(y, m - 1, d) / 86400000);
        var raw = nativeBridge.getDayTimes(epochDay, state.lat, state.lng, state.zone,
          state.method, state.asr, state.highLat);
        var parsed = validTimes(JSON.parse(raw));
        if (parsed) return parsed;
      } catch (e) { /* fall back to JavaScript */ }
    }
    return calcDay(y, m, d);
  }

  function monthTimesFor(y, m) {
    if (nativeBridge) {
      try {
        var raw = nativeBridge.getMonthTimes(y, m, state.lat, state.lng, state.zone,
          state.method, state.asr, state.highLat);
        var parsed = JSON.parse(raw);
        if (parsed && parsed.length) {
          var ok = true;
          for (var i = 0; i < parsed.length; i++) {
            if (!validTimes(parsed[i])) { ok = false; break; }
          }
          if (ok) return parsed;
        }
      } catch (e) { /* fall back to JavaScript */ }
    }
    var out = [];
    var days = new Date(Date.UTC(y, m, 0)).getUTCDate();
    for (var day = 1; day <= days; day++) out.push(calcDay(y, m, day));
    return out;
  }

  function is24HourLocale() {
    try {
      var opts = new Intl.DateTimeFormat(undefined, { hour: "numeric" }).resolvedOptions();
      if (typeof opts.hour12 === "boolean") return !opts.hour12;
      if (opts.hourCycle) return opts.hourCycle === "h23" || opts.hourCycle === "h24";
    } catch (e) { /* ignore */ }
    return true;
  }

  function formatTime(hours) {
    var h = normalize(hours, 24);
    var total = Math.round(h * 60);
    var hour = Math.floor(total / 60) % 24;
    var minute = total % 60;
    var use24 = state.timeFormat === "24" ||
      (state.timeFormat === "auto" && is24HourLocale());
    if (use24) return digits(pad2(hour) + ":" + pad2(minute));
    var suffix = hour >= 12 ? tr("pm") : tr("am");
    var display = hour % 12;
    if (display === 0) display = 12;
    return digits(display + ":" + pad2(minute)) + " " + suffix;
  }

  function hms(seconds) {
    seconds = Math.max(0, Math.floor(seconds));
    var h = Math.floor(seconds / 3600);
    var m = Math.floor((seconds % 3600) / 60);
    var s = seconds % 60;
    return digits(pad2(h) + ":" + pad2(m) + ":" + pad2(s));
  }

  // ---------------------------------------------------------------- theme

  function effectiveDark() {
    if (state.theme === "dark") return true;
    if (state.theme === "light") return false;
    try {
      return !!(window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
    } catch (e) {
      return false;
    }
  }

  function applyTheme() {
    var root = document.documentElement;
    if (state.theme) root.setAttribute("data-theme", state.theme);
    else root.removeAttribute("data-theme");
    var dark = effectiveDark();
    if (el.themeColor) el.themeColor.setAttribute("content", dark ? "#0c1511" : "#f5f7f2");
    if (nativeBridge) {
      try { nativeBridge.setNativeTheme(dark); } catch (e) { /* ignore */ }
    }
  }

  function toggleTheme() {
    state.theme = effectiveDark() ? "light" : "dark";
    applyTheme();
    saveLocal();
  }

  // ---------------------------------------------------------------- selects / text

  function updateGpsButton() {
    el.gpsBtn.textContent = "📍 " + (state.gpsBusy ? tr("locating") : tr("gps"));
    el.gpsBtn.disabled = state.gpsBusy;
  }

  function updateAlertButton() {
    el.alertBtn.textContent = state.mask === 0 ? tr("enableAll") : tr("disableAll");
  }

  function updateMonthButton() {
    el.monthBtn.textContent = state.monthOpen ? tr("mHide") : tr("mBtn");
    el.monthBtn.setAttribute("aria-expanded", String(state.monthOpen));
  }

  function buildLanguageSelect() {
    var select = el.langSelect;
    select.textContent = "";
    Object.keys(LANGUAGE_NAMES).forEach(function (code) {
      var option = document.createElement("option");
      option.value = code;
      option.textContent = LANGUAGE_NAMES[code];
      select.appendChild(option);
    });
    select.value = state.lang;
    select.setAttribute("aria-label", tr("language"));
  }

  function buildCitySelect() {
    var select = el.citySelect;
    select.textContent = "";
    CITIES.forEach(function (city) {
      var option = document.createElement("option");
      option.value = city[0];
      option.textContent = state.lang === "ar" ? city[1] : city[2];
      select.appendChild(option);
    });
    if (state.custom) {
      var custom = document.createElement("option");
      custom.value = "custom";
      custom.textContent = state.name || tr("custom");
      select.appendChild(custom);
      select.value = "custom";
    } else {
      select.value = findCity(state.cityId) ? state.cityId : "riyadh";
    }
    select.setAttribute("aria-label", tr("city"));
  }

  function fillSelect(select, keys, labels, current) {
    select.textContent = "";
    keys.forEach(function (key) {
      var option = document.createElement("option");
      option.value = key;
      option.textContent = labels[key] || key;
      select.appendChild(option);
    });
    select.value = current;
  }

  function updateSettingsUI() {
    el.settingsTitle.textContent = tr("settings");
    el.methodLabel.textContent = tr("methodLabel");
    el.asrLabel.textContent = tr("asrLabel");
    el.highLatLabel.textContent = tr("highLatLabel");
    el.timeFormatLabel.textContent = tr("timeFormatLabel");
    el.settingsNote.textContent = tr("settingsNote");
    el.settingsClose.setAttribute("aria-label", tr("close"));

    fillSelect(el.methodSelect, Object.keys(METHOD_DEFS), tr("methods"), state.method);
    fillSelect(el.asrSelect, Object.keys(ASR_DEFS), tr("asrMethods"), state.asr);
    fillSelect(el.highLatSelect, HIGH_LAT_DEFS, tr("highLatRules"), state.highLat);
    fillSelect(el.timeFormatSelect, ["auto", "12", "24"], {
      auto: tr("formatAuto"), "12": tr("format12"), "24": tr("format24")
    }, state.timeFormat);
  }

  function updateDateLine() {
    var d = state.local;
    var reference = new Date(Date.UTC(d.y, d.m - 1, d.d, 12));
    var hijri, gregorian;
    try {
      hijri = new Intl.DateTimeFormat(DATE_LOCALES[state.lang] || DATE_LOCALES.en, {
        day: "numeric", month: "long", year: "numeric", timeZone: "UTC"
      }).format(reference);
      gregorian = new Intl.DateTimeFormat(gregLocale(), {
        weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC"
      }).format(reference);
    } catch (e) {
      hijri = d.y + "-" + pad2(d.m) + "-" + pad2(d.d);
      gregorian = "";
    }
    el.dates.textContent = gregorian ? hijri + " · " + gregorian : hijri;
  }

  function renderText() {
    document.documentElement.lang = state.lang;
    document.documentElement.dir = (state.lang === "ar" || state.lang === "ur") ? "rtl" : "ltr";
    document.title = tr("title");
    el.title.textContent = tr("title");
    el.subtitle.textContent = tr("methods")[state.method] || tr("method");
    el.nextLabel.textContent = tr("next");
    el.qiblaTitle.textContent = "🕋 " + tr("qt");
    el.qiblaNote.textContent = tr("qn");
    el.compassBtn.textContent = state.compassOn ? tr("stopCompass") : tr("compass");
    el.qiblaSvg.setAttribute("aria-label", tr("qt"));

    el.themeBtn.setAttribute("aria-label", tr("theme"));
    el.settingsBtn.setAttribute("aria-label", tr("settings"));
    el.compassBtn.setAttribute("aria-label", state.compassOn ? tr("stopCompass") : tr("compass"));
    el.monthPrev.setAttribute("aria-label", tr("monthPrev"));
    el.monthNext.setAttribute("aria-label", tr("monthNext"));
    el.monthBtn.setAttribute("aria-label", state.monthOpen ? tr("mHide") : tr("mBtn"));

    el.permTitle.textContent = tr("permTitle");
    el.permNotifLabel.textContent = tr("permNotif");
    el.permExactLabel.textContent = tr("permExact");
    el.permBatteryLabel.textContent = tr("permBattery");

    updateGpsButton();
    updateAlertButton();
    updateMonthButton();
    updateSettingsUI();
    buildLanguageSelect();
    buildCitySelect();
    updateDateLine();
    if (!state.compassOn) el.compassState.textContent = "";
    else updateCompassBadge();
  }

  // ---------------------------------------------------------------- prayer list

  function renderPrayerList() {
    var names = tr("names");
    el.prayerList.textContent = "";
    for (var i = 0; i < 6; i++) {
      var row = document.createElement("div");
      row.className = "p" + (i === 1 ? " sunrise no-alert" : "");
      row.setAttribute("data-prayer", String(i));

      var name = document.createElement("span");
      name.className = "p-name";
      name.textContent = names[i];

      var time = document.createElement("span");
      time.className = "p-time";
      time.textContent = formatTime(state.times[i]);

      row.appendChild(name);
      row.appendChild(time);

      if (i !== 1) {
        var button = document.createElement("button");
        button.type = "button";
        button.className = "prayer-alert";
        button.setAttribute("data-index", String(i));
        button.addEventListener("click", (function (index) {
          return function () {
            setPrayerEnabled(index, (state.mask & PRAYER_BITS[index]) === 0);
          };
        })(i));
        row.appendChild(button);
      }
      el.prayerList.appendChild(row);
    }
    updatePrayerToggles();
    updateNextRow();
  }

  function updatePrayerToggles() {
    var names = tr("names");
    var rows = el.prayerList.querySelectorAll(".p[data-prayer]");
    for (var r = 0; r < rows.length; r++) {
      var index = +rows[r].getAttribute("data-prayer");
      if (index === 1) continue;
      var on = (state.mask & PRAYER_BITS[index]) !== 0;
      var button = rows[r].querySelector(".prayer-alert");
      if (!button) continue;
      var label = (on ? tr("prayerOn") : tr("prayerOff")) + names[index];
      button.textContent = on ? "🔔" : "🔕";
      button.setAttribute("aria-pressed", String(on));
      button.setAttribute("aria-label", label);
      button.title = label;
    }
  }

  function updateNextRow() {
    var rows = el.prayerList.querySelectorAll(".p[data-prayer]");
    for (var r = 0; r < rows.length; r++) {
      var index = +rows[r].getAttribute("data-prayer");
      var isNext = !state.nextTomorrow && index === state.nextIndex;
      rows[r].classList.toggle("next", isNext);
    }
  }

  function updateNextDisplay() {
    if (state.nextIndex < 0) return;
    el.nextName.textContent = tr("names")[state.nextIndex];
  }

  function updateSchedule() {
    if (!state.times || state.times.length < 6) return;
    var lp = state.local || localParts(new Date());
    var now = Date.now();
    var best = -1;
    var bestAt = Infinity;
    for (var i = 0; i < 6; i++) {
      var at = localHoursToMillis(lp.y, lp.m, lp.d, state.times[i], state.zone);
      if (isFinite(at) && at > now + 500 && at < bestAt) {
        bestAt = at;
        best = i;
      }
    }

    var tomorrow = false;
    var tomorrowTimes = null;
    if (best < 0) {
      var nextDate = addDays(lp.y, lp.m, lp.d, 1);
      tomorrowTimes = dayTimesFor(nextDate.y, nextDate.m, nextDate.d);
      bestAt = localHoursToMillis(nextDate.y, nextDate.m, nextDate.d, tomorrowTimes[0], state.zone);
      best = 0;
      tomorrow = true;
    }

    if (!isFinite(bestAt)) bestAt = now + 3600000;
    state.nextAt = bestAt;
    state.nextIndex = best;
    state.nextTomorrow = tomorrow;
    state.tomorrowTimes = tomorrowTimes;
    updateNextDisplay();
    updateNextRow();
  }

  function refreshAll() {
    state.local = localParts(new Date());
    state.dateKey = state.local.epochDay;
    state.times = dayTimesFor(state.local.y, state.local.m, state.local.d);
    renderText();
    renderPrayerList();
    updateDateLine();
    updateSchedule();
    updateDeclination();
    updateQibla();
    if (state.monthOpen) buildMonth();
    refreshPermissionState();
  }

  function tick() {
    if (!state.times || state.times.length < 6) return;
    var lp = localParts(new Date());
    if (lp.epochDay !== state.dateKey) {
      refreshAll();
      return;
    }
    var now = Date.now();
    if (!state.nextAt || now >= state.nextAt) updateSchedule();
    var remain = state.nextAt - now;
    if (!isFinite(remain) || remain < 0) remain = 0;
    el.countdown.textContent = hms(remain / 1000);
    maybeFire(now);
  }

  // ---------------------------------------------------------------- qibla

  function qiblaBearing() {
    var p1 = state.lat * RAD;
    var p2 = KAABA.lat * RAD;
    var dl = (KAABA.lng - state.lng) * RAD;
    return normalize(Math.atan2(
      Math.sin(dl) * Math.cos(p2),
      Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
    ) * DEG, 360);
  }

  function applyQibla(heading) {
    var bearing = qiblaBearing();
    var h = heading === null || heading === undefined ? 0 : heading;
    h = normalize(h + (state.declination || 0), 360);
    el.qiblaDeg.textContent = digits(Math.round(bearing)) + "° " + tr("from");
    el.dial.setAttribute("transform", "rotate(" + (-h) + " 120 120)");
    el.needle.setAttribute("transform", "rotate(" + (bearing - h) + " 120 120)");
    el.qiblaSvg.setAttribute("aria-label", tr("qt") + " " + Math.round(bearing) + "° " + tr("from"));
  }

  function updateQibla() {
    applyQibla(state.compassOn && state.smooth !== null ? state.smooth : null);
  }

  function updateDeclination() {
    state.declination = 0;
    if (!nativeBridge) return;
    try {
      var value = Number(nativeBridge.getDeclination(state.lat, state.lng));
      if (isFinite(value)) state.declination = value;
    } catch (e) { /* ignore */ }
  }

  // ---------------------------------------------------------------- fallback alerts

  function beep() {
    try {
      var Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return;
      var ctx = new Ctx();
      [523, 659, 784, 659, 523].forEach(function (frequency, index) {
        var oscillator = ctx.createOscillator();
        var gain = ctx.createGain();
        oscillator.frequency.value = frequency;
        oscillator.connect(gain);
        gain.connect(ctx.destination);
        var start = ctx.currentTime + index * 0.35;
        gain.gain.setValueAtTime(0.0001, start);
        gain.gain.exponentialRampToValueAtTime(0.3, start + 0.05);
        gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.33);
        oscillator.start(start);
        oscillator.stop(start + 0.34);
      });
      setTimeout(function () { try { ctx.close(); } catch (e) { /* ignore */ } }, 2600);
    } catch (e) { /* audio may need a user gesture */ }
  }

  function fireFallback(index) {
    beep();
    var message = tr("time") + tr("names")[index];
    try {
      if (typeof Notification !== "undefined" && Notification.permission === "granted") {
        new Notification(message);
      }
    } catch (e) { /* ignore */ }
    toast("🕌 " + message);
  }

  function maybeFire(now) {
    if (nativeBridge || state.mask === 0 || !state.times || state.times.length < 6) return;
    var lp = state.local;
    if (!lp) return;
    for (var i = 0; i < 6; i++) {
      if (i === 1 || (state.mask & PRAYER_BITS[i]) === 0) continue;
      var at = localHoursToMillis(lp.y, lp.m, lp.d, state.times[i], state.zone);
      if (now >= at && now - at < 60000) {
        var key = lp.epochDay * 10 + i;
        if (state.lastFired !== key) {
          state.lastFired = key;
          fireFallback(i);
        }
      }
    }
  }

  function setPrayerEnabled(index, on) {
    if (on) state.mask |= PRAYER_BITS[index];
    else state.mask &= ~PRAYER_BITS[index];
    state.mask &= ALL_PRAYER_MASK;
    saveLocal();
    syncNative();
    updateAlertButton();
    updatePrayerToggles();
    refreshPermissionState();
    if (on) requestNotificationPermission();
  }

  // ---------------------------------------------------------------- permissions

  function setPermRow(row, stateEl, button, ok, action, battery) {
    row.classList.toggle("ok", !!ok);
    stateEl.textContent = ok ? tr("permAllowed")
      : (battery ? tr("batteryRestricted") : tr("permOff"));
    button.textContent = tr("permFix");
    button.onclick = function () {
      if (action === "notifications") {
        requestNotificationPermission();
      } else if (action === "exact" && nativeBridge) {
        try { nativeBridge.openExactAlarmSettings(); } catch (e) { /* ignore */ }
      } else if (action === "battery" && nativeBridge) {
        try { nativeBridge.openBatterySettings(); } catch (e) { /* ignore */ }
      }
    };
  }

  function refreshPermissionState() {
    if (!el.permCard) return;
    var notifications = true;
    var exactAlarms = true;
    var battery = true;

    if (nativeBridge) {
      try {
        var value = JSON.parse(nativeBridge.getPermissionState());
        notifications = value.notifications !== false;
        exactAlarms = value.exactAlarms !== false;
        battery = value.battery !== false;
      } catch (e) { /* keep defaults */ }
    } else if (typeof Notification !== "undefined") {
      notifications = Notification.permission === "granted";
    }

    var show = state.mask !== 0 && (!notifications || !exactAlarms || !battery);
    el.permCard.hidden = !show;
    if (!show) return;

    setPermRow(el.permNotifRow, el.permNotifState, el.permNotifBtn,
      notifications, "notifications", false);
    setPermRow(el.permExactRow, el.permExactState, el.permExactBtn,
      exactAlarms, "exact", false);
    setPermRow(el.permBatteryRow, el.permBatteryState, el.permBatteryBtn,
      battery, "battery", true);
  }

  function requestNotificationPermission() {
    if (nativeBridge) {
      try { nativeBridge.requestNotificationPermission(); } catch (e) { /* ignore */ }
      return;
    }
    if (typeof Notification === "undefined") {
      refreshPermissionState();
      return;
    }
    if (Notification.permission === "granted") {
      refreshPermissionState();
      return;
    }
    try {
      var result = Notification.requestPermission(function (permission) {
        if (permission !== "granted") toast(tr("permissionDenied"));
        refreshPermissionState();
      });
      if (result && typeof result.then === "function") {
        result.then(function (permission) {
          if (permission !== "granted") toast(tr("permissionDenied"));
          refreshPermissionState();
        });
      }
    } catch (e) {
      refreshPermissionState();
    }
  }

  // ---------------------------------------------------------------- month view

  function monthLabel(year, month) {
    try {
      return new Intl.DateTimeFormat(gregLocale(), {
        month: "long", year: "numeric", timeZone: "UTC"
      }).format(new Date(Date.UTC(year, month - 1, 1, 12)));
    } catch (e) {
      return year + "-" + pad2(month);
    }
  }

  function buildMonth() {
    if (!state.monthOpen) return;
    var lp = state.local || localParts(new Date());
    if (!state.monthCursor) state.monthCursor = { y: lp.y, m: lp.m };
    var year = state.monthCursor.y;
    var month = state.monthCursor.m;
    var values = monthTimesFor(year, month);

    el.monthLabel.textContent = monthLabel(year, month);
    el.monthTable.textContent = "";

    var thead = document.createElement("thead");
    var headRow = document.createElement("tr");
    [tr("gr"), tr("hj")].forEach(function (label) {
      var th = document.createElement("th");
      th.textContent = label;
      headRow.appendChild(th);
    });
    tr("names").forEach(function (label) {
      var th = document.createElement("th");
      th.textContent = label;
      headRow.appendChild(th);
    });
    thead.appendChild(headRow);
    el.monthTable.appendChild(thead);

    var tbody = document.createElement("tbody");
    var hijriFormatter = null;
    try {
      hijriFormatter = new Intl.DateTimeFormat(DATE_LOCALES[state.lang] || DATE_LOCALES.en, {
        day: "numeric", timeZone: "UTC"
      });
    } catch (e) { hijriFormatter = null; }

    for (var day = 1; day <= values.length; day++) {
      var row = document.createElement("tr");
      var reference = new Date(Date.UTC(year, month - 1, day, 12));
      if (year === lp.y && month === lp.m && day === lp.d) row.className = "today";

      var gregorianCell = document.createElement("td");
      gregorianCell.textContent = digits(day);
      row.appendChild(gregorianCell);

      var hijriCell = document.createElement("td");
      hijriCell.textContent = hijriFormatter ? hijriFormatter.format(reference) : "";
      row.appendChild(hijriCell);

      var times = values[day - 1];
      for (var i = 0; i < 6; i++) {
        var cell = document.createElement("td");
        cell.textContent = formatTime(times[i]);
        row.appendChild(cell);
      }
      tbody.appendChild(row);
    }
    el.monthTable.appendChild(tbody);
  }

  function toggleMonth() {
    state.monthOpen = !state.monthOpen;
    el.month.hidden = !state.monthOpen;
    if (state.monthOpen) {
      var lp = state.local || localParts(new Date());
      state.monthCursor = { y: lp.y, m: lp.m };
      buildMonth();
    }
    updateMonthButton();
  }

  function shiftMonth(delta) {
    var lp = state.local || localParts(new Date());
    var cursor = state.monthCursor || { y: lp.y, m: lp.m };
    var date = new Date(Date.UTC(cursor.y, cursor.m - 1 + delta, 1));
    state.monthCursor = { y: date.getUTCFullYear(), m: date.getUTCMonth() + 1 };
    buildMonth();
  }

  // ---------------------------------------------------------------- compass

  function updateCompassBadge() {
    if (!state.compassOn) {
      el.compassState.textContent = "";
      return;
    }
    if (state.heading === null) {
      el.compassState.textContent = (Date.now() - state.lastHeadingAt > 4500)
        ? tr("compassUnavailable") : tr("compassWaiting");
      return;
    }
    var label = tr("compassOn");
    if (state.compassAccuracy) {
      var key = "accuracy" + state.compassAccuracy.charAt(0).toUpperCase() +
        state.compassAccuracy.slice(1);
      var accuracy = tr(key);
      if (accuracy && accuracy !== key) label += " · " + accuracy;
    }
    el.compassState.textContent = label;
  }

  function setHeading(raw) {
    if (!isFiniteNumber(raw)) return;
    state.heading = normalize(raw, 360);
    state.lastHeadingAt = Date.now();
    if (state.smooth === null) state.smooth = state.heading;
    updateCompassBadge();
  }

  function setCompassAccuracy(label) {
    if (label !== "high" && label !== "medium" && label !== "low" && label !== "unreliable") {
      return;
    }
    state.compassAccuracy = label;
    updateCompassBadge();
  }

  function setAccuracyFromDegrees(accuracy) {
    if (!isFinite(accuracy) || accuracy < 0) state.compassAccuracy = "unreliable";
    else if (accuracy <= 15) state.compassAccuracy = "high";
    else if (accuracy <= 30) state.compassAccuracy = "medium";
    else state.compassAccuracy = "low";
    updateCompassBadge();
  }

  function smoothHeading(raw) {
    if (state.smooth === null) {
      state.smooth = raw;
      return;
    }
    var delta = normalize(raw - state.smooth + 180, 360) - 180;
    state.smooth = normalize(state.smooth + delta * 0.2, 360);
  }

  function compassFrame() {
    if (!state.compassOn) {
      state.raf = 0;
      return;
    }
    if (state.heading !== null) {
      smoothHeading(state.heading);
      applyQibla(state.smooth);
    }
    state.raf = window.requestAnimationFrame(compassFrame);
  }

  function startCompass() {
    if (state.compassOn) return;
    state.compassOn = true;
    state.heading = null;
    state.smooth = null;
    state.compassAccuracy = "";
    state.lastHeadingAt = 0;
    el.compassBtn.textContent = tr("stopCompass");
    el.compassBtn.setAttribute("aria-label", tr("stopCompass"));
    updateCompassBadge();

    var nativeStarted = false;
    if (nativeBridge) {
      try { nativeStarted = !!nativeBridge.startCompass(); } catch (e) { nativeStarted = false; }
    }
    if (nativeStarted) state.compassSource = "native";
    else startWebCompass();

    if (state.raf) window.cancelAnimationFrame(state.raf);
    state.raf = window.requestAnimationFrame(compassFrame);

    if (state.compassTimeout) clearTimeout(state.compassTimeout);
    state.compassTimeout = setTimeout(function () {
      if (state.compassOn && state.heading === null) {
        updateCompassBadge();
        toast(tr("noCompassToast"));
      }
    }, 4800);
  }

  function startWebCompass() {
    state.compassSource = "web";
    var attach = function () {
      if (state.webListening) return;
      window.addEventListener("deviceorientationabsolute", onOrient, true);
      window.addEventListener("deviceorientation", onOrient, true);
      state.webListening = true;
    };

    if (typeof DeviceOrientationEvent !== "undefined" &&
        typeof DeviceOrientationEvent.requestPermission === "function") {
      DeviceOrientationEvent.requestPermission().then(function (result) {
        if (result === "granted") attach();
        else {
          toast(tr("compassUnavailable"));
          stopCompass();
        }
      }).catch(function () { stopCompass(); });
    } else {
      attach();
    }
  }

  function stopWebCompass() {
    if (!state.webListening) return;
    window.removeEventListener("deviceorientationabsolute", onOrient, true);
    window.removeEventListener("deviceorientation", onOrient, true);
    state.webListening = false;
  }

  function stopCompass() {
    state.compassOn = false;
    state.heading = null;
    state.smooth = null;
    state.compassAccuracy = "";
    state.lastHeadingAt = 0;
    if (state.raf) {
      window.cancelAnimationFrame(state.raf);
      state.raf = 0;
    }
    if (state.compassTimeout) {
      clearTimeout(state.compassTimeout);
      state.compassTimeout = 0;
    }
    if (nativeBridge) {
      try { nativeBridge.stopCompass(); } catch (e) { /* ignore */ }
    }
    stopWebCompass();
    el.compassBtn.textContent = tr("compass");
    el.compassBtn.setAttribute("aria-label", tr("compass"));
    el.compassState.textContent = "";
    applyQibla(null);
  }

  function toggleCompass() {
    if (state.compassOn) stopCompass();
    else startCompass();
  }

  function onOrient(event) {
    var heading = null;
    if (typeof event.webkitCompassHeading === "number" &&
        isFinite(event.webkitCompassHeading)) {
      heading = normalize(event.webkitCompassHeading, 360);
      if (typeof event.webkitCompassAccuracy === "number") {
        setAccuracyFromDegrees(event.webkitCompassAccuracy);
      }
    } else if (event.alpha !== null && event.alpha !== undefined &&
               (event.absolute || event.type === "deviceorientationabsolute")) {
      heading = normalize(360 - event.alpha, 360);
    }
    if (heading !== null) setHeading(heading);
  }

  window.setNativeHeading = function (degrees) { setHeading(Number(degrees)); };
  window.setCompassAccuracy = function (label) { setCompassAccuracy(label); };

  // ---------------------------------------------------------------- location

  function applyLocation(lat, lng) {
    if (!isFiniteNumber(lat) || !isFiniteNumber(lng) ||
        lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      locationFailed("unavailable");
      return;
    }
    state.gpsBusy = false;
    state.locationSource = "";
    state.lat = lat;
    state.lng = lng;
    state.custom = true;
    state.cityId = "";
    state.name = "";
    state.zone = detectZone();
    saveLocal();
    syncNative();
    refreshAll();
    toast(tr("locUpdated"));
    if (nativeBridge) {
      try { nativeBridge.lookupCity(state.lat, state.lng, state.lang); } catch (e) { /* ignore */ }
    }
  }

  function locationFailed(code) {
    state.gpsBusy = false;
    state.locationSource = "";
    updateGpsButton();
    toast(code === "denied" ? tr("gpsDenied") : tr("gpsErr"));
  }

  function locateWithBrowser() {
    if (!navigator.geolocation) {
      locationFailed("unavailable");
      return;
    }
    navigator.geolocation.getCurrentPosition(function (position) {
      applyLocation(position.coords.latitude, position.coords.longitude);
    }, function (error) {
      locationFailed(error && error.code === 1 ? "denied" : "unavailable");
    }, { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 });
  }

  function locate() {
    if (state.gpsBusy) return;
    state.gpsBusy = true;
    state.locationSource = "native";
    updateGpsButton();

    var nativeStarted = false;
    if (nativeBridge && typeof nativeBridge.requestLocation === "function") {
      try {
        nativeBridge.requestLocation();
        nativeStarted = true;
      } catch (e) {
        nativeStarted = false;
      }
    }
    if (!nativeStarted) {
      state.locationSource = "web";
      locateWithBrowser();
    }
  }

  window.setNativeLocation = function (lat, lng) {
    applyLocation(Number(lat), Number(lng));
  };

  window.setNativeLocationError = function (code) {
    if (!state.gpsBusy) return;
    if (code === "unavailable" && state.locationSource === "native") {
      // No usable provider in the native layer; try the WebView geolocation
      // implementation before giving up.
      state.locationSource = "web";
      locateWithBrowser();
      return;
    }
    locationFailed(code === "denied" ? "denied" : "unavailable");
  };

  window.setAcquiredCity = function (name) {
    if (!state.custom || typeof name !== "string" || !name.trim()) return;
    state.name = name.trim();
    saveLocal();
    buildCitySelect();
  };

  // ---------------------------------------------------------------- events

  function selectCity(id) {
    if (id === "custom") return;
    var city = findCity(id);
    if (!city) {
      buildCitySelect();
      return;
    }
    state.cityId = city[0];
    state.lat = city[3];
    state.lng = city[4];
    state.zone = "Asia/Riyadh";
    state.custom = false;
    state.name = "";
    saveLocal();
    syncNative();
    refreshAll();
  }

  function onSettingChange() {
    state.method = METHOD_DEFS[el.methodSelect.value] ? el.methodSelect.value : "UMM_AL_QURA";
    state.asr = ASR_DEFS[el.asrSelect.value] ? el.asrSelect.value : "STANDARD";
    state.highLat = HIGH_LAT_DEFS.indexOf(el.highLatSelect.value) >= 0
      ? el.highLatSelect.value : "ANGLE_BASED";
    state.timeFormat = (el.timeFormatSelect.value === "12" ||
      el.timeFormatSelect.value === "24") ? el.timeFormatSelect.value : "auto";
    saveLocal();
    syncNative();
    refreshAll();
  }

  function toggleAllAlerts() {
    if (state.mask === 0) {
      state.mask = ALL_PRAYER_MASK;
      toast(tr("alertOnToast"));
      requestNotificationPermission();
    } else {
      state.mask = 0;
      toast(tr("alertOffToast"));
    }
    saveLocal();
    syncNative();
    updateAlertButton();
    updatePrayerToggles();
    refreshPermissionState();
  }

  function openSettings() {
    el.settingsSheet.hidden = false;
    document.body.classList.add("sheet-open");
    updateSettingsUI();
    try { el.settingsClose.focus({ preventScroll: true }); }
    catch (e) { try { el.settingsClose.focus(); } catch (ignore) { /* ignore */ } }
  }

  function closeSettings() {
    el.settingsSheet.hidden = true;
    document.body.classList.remove("sheet-open");
    try { el.settingsBtn.focus({ preventScroll: true }); }
    catch (e) { /* ignore */ }
  }

  function bindEvents() {
    el.themeBtn.addEventListener("click", toggleTheme);
    el.settingsBtn.addEventListener("click", openSettings);
    el.settingsClose.addEventListener("click", closeSettings);
    el.settingsBackdrop.addEventListener("click", closeSettings);
    el.langSelect.addEventListener("change", function () {
      if (!T_HAS(this.value)) return;
      state.lang = this.value;
      saveLocal();
      syncNative();
      refreshAll();
    });
    el.citySelect.addEventListener("change", function () { selectCity(this.value); });
    el.gpsBtn.addEventListener("click", locate);
    el.alertBtn.addEventListener("click", toggleAllAlerts);
    el.monthBtn.addEventListener("click", toggleMonth);
    el.monthPrev.addEventListener("click", function () { shiftMonth(-1); });
    el.monthNext.addEventListener("click", function () { shiftMonth(1); });
    el.compassBtn.addEventListener("click", toggleCompass);
    el.methodSelect.addEventListener("change", onSettingChange);
    el.asrSelect.addEventListener("change", onSettingChange);
    el.highLatSelect.addEventListener("change", onSettingChange);
    el.timeFormatSelect.addEventListener("change", onSettingChange);
    el.toast.addEventListener("click", function () { el.toast.hidden = true; });

    document.addEventListener("keydown", function (event) {
      if ((event.key === "Escape" || event.keyCode === 27) && !el.settingsSheet.hidden) {
        closeSettings();
      }
    });

    document.addEventListener("visibilitychange", function () {
      if (document.hidden) return;
      refreshAll();
      refreshPermissionState();
    });
  }

  window.refreshPermissionState = function () { refreshPermissionState(); };

  // ---------------------------------------------------------------- init

  function init() {
    state.lang = T_HAS(detected) ? detected : "en";
    loadLocalPrefs();
    loadNativeConfig();
    if (!T_HAS(state.lang)) state.lang = T_HAS(detected) ? detected : "en";
    if (!state.zone) state.zone = detectZone();

    applyTheme();
    bindEvents();
    updateDeclination();
    refreshAll();
    syncNative();
    tick();
    setInterval(tick, 500);

    try {
      var media = window.matchMedia("(prefers-color-scheme: dark)");
      var onMediaChange = function () { if (!state.theme) applyTheme(); };
      if (media.addEventListener) media.addEventListener("change", onMediaChange);
      else if (media.addListener) media.addListener(onMediaChange);
    } catch (e) { /* ignore */ }
  }

  init();
})();
