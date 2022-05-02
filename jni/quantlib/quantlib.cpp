#include <ql/quantlib.hpp>
#include "com_lunar_pricing_QuantLibAdaptor.h"

using namespace QuantLib;

    class YieldCurve {
    public:
        YieldCurve(const Date& valueDate, const Real& riskFreeRate, const DayCounter& dayCounter) {
            FlatForward* pRiskFreeTermStructure = new FlatForward(valueDate, riskFreeRate, dayCounter);
            m_riskFreeTermStructure = boost::shared_ptr<YieldTermStructure>(pRiskFreeTermStructure);
        }

        boost::shared_ptr<YieldTermStructure>& getRiskFreeTermStructure() {
            return m_riskFreeTermStructure;
        }

    private:
        boost::shared_ptr<YieldTermStructure> m_riskFreeTermStructure;
    };

    class DividendCurve {
    public:
        DividendCurve() {

        }

        void addPoint(const Date& date, const Real& amount) {
            m_dates.push_back(date);
            m_amounts.push_back(amount);
        }

        const std::vector<Date>& getDates() const {
            return m_dates;
        }

        const std::vector<Real>& getAmounts() const {
            return m_amounts;
        }

    private:
        std::vector<Date> m_dates;
        std::vector<Real> m_amounts;
    };

    class CurveManager {
    public:
        CurveManager() {
            m_yieldCurve = 0;
        }

        ~CurveManager() {
            if (m_yieldCurve != 0) {
                delete m_yieldCurve;
            }
            for (std::map<int64_t, DividendCurve*>::iterator i = m_dividendCurves.begin(); i != m_dividendCurves.end(); i++) {
                delete i->second;
            }
        }

        DividendCurve* createDividendCurve(const int64_t& sid) {
            DividendCurve* dividendCurve = new DividendCurve();
            m_dividendCurves[sid] = dividendCurve;
            return dividendCurve;
        }

        DividendCurve* getDividendCurve(const int64_t& sid) const {
            std::map<int64_t, DividendCurve*>::const_iterator i = m_dividendCurves.find(sid);
            if (i == m_dividendCurves.end())
                return 0;
            return i->second;
        }

        YieldCurve* createYieldCurve(const Date& valueDate, const Real& riskFreeRate, const DayCounter& dayCounter) {
            m_yieldCurve = new YieldCurve(valueDate, riskFreeRate, dayCounter);
            return m_yieldCurve;
        }

        YieldCurve* getYieldCurve() const {
            return m_yieldCurve;
        }

    private:
        std::map<int64_t, DividendCurve*> m_dividendCurves;
        YieldCurve* m_yieldCurve;
    };


    class EuroPricingObject {
    public:
        EuroPricingObject(const Date& valueDate, const Date& matureDate, const Option::Type& optionType, const Real& strike, const DividendCurve* dividendCurve) {
            m_valueDate = valueDate;
            m_matureDate = matureDate;
            m_optionType = optionType;
            m_strike = strike;

            PlainVanillaPayoff* pPayoff = new PlainVanillaPayoff(optionType, strike);
            m_payoff = boost::shared_ptr<StrikedTypePayoff>(pPayoff);

            Exercise* pEuroExercise = new EuropeanExercise(matureDate);
            m_exercise = boost::shared_ptr<Exercise>(pEuroExercise);

            std::vector<Date> divDates;
            std::vector<Real> divAmounts;
            std::vector<Date>::const_iterator i;
            std::vector<Real>::const_iterator j;
            for (i = dividendCurve->getDates().begin(), j = dividendCurve->getAmounts().begin(); i != dividendCurve->getDates().end(); i++, j++) {
                if (*i < matureDate) {
                    divDates.push_back(*i);
                    divAmounts.push_back(*j);
                }
            }

            m_euroOption = new DividendVanillaOption(m_payoff, m_exercise, divDates, divAmounts);
        }

        ~EuroPricingObject() {
            delete m_euroOption;
        }

        DividendVanillaOption* getOption() {
            return m_euroOption;
        }

    private:
        Date m_valueDate;
        Date m_matureDate;
        Option::Type m_optionType;
        Real m_strike;

        boost::shared_ptr<Exercise> m_exercise;
        boost::shared_ptr<StrikedTypePayoff> m_payoff;
        DividendVanillaOption* m_euroOption;
    };

    class EuroPricer {
    public:
        EuroPricer(const Date& valueDate, CurveManager* curveManager) {
            m_calendar = TARGET();
            m_dayCounter = Actual365Fixed();
            m_valueDate = valueDate;
            m_curveManager = curveManager;
        }

        EuroPricingObject* addEuroPricingObject(const int64_t& sid, const Date& matureDate, const Option::Type& optionType, const Real& strike, const DividendCurve* dividendCurve) {
            m_pricingObjects[sid] = new EuroPricingObject(m_valueDate, matureDate, optionType, strike, dividendCurve);
        }

        void removeEuroPricingObject(const int64_t& sid) {
            m_pricingObjects.erase(sid);
        }

        bool isExistEuroPricingOption(const int64_t& sid) {
            return m_pricingObjects.find(sid) != m_pricingObjects.end();
        }

        EuroPricingObject* priceEuroOption(const int64_t& sid, const int64_t& undSid, const Real& spot, const Real& price, Volatility& impliedVol) {
            std::map<int64_t, EuroPricingObject*>::iterator objectIterator = m_pricingObjects.find(sid);
            if (objectIterator == m_pricingObjects.end()) {
                return 0;
            }

            EuroPricingObject* pricingObject = objectIterator->second;

            Quote* pSpot = new SimpleQuote(spot);
            boost::shared_ptr<Quote> hSpot(pSpot);

            impliedVol = (Volatility)0.25;
            priceEuroOption(pricingObject, hSpot, price, impliedVol, true);
            priceEuroOption(pricingObject, hSpot, price, impliedVol, false);

            return pricingObject;
        }

        const Date& getValueDate() const {
            return m_valueDate;
        }   

    private:
        EuroPricingObject* priceEuroOption(EuroPricingObject* pricingObject, boost::shared_ptr<Quote>& hSpot, const Real& price, Volatility& vol, bool findImpliedVol) {
            YieldCurve* yieldCurve = m_curveManager->getYieldCurve();
            BlackConstantVol* pVolTermStructure = new BlackConstantVol(m_valueDate, m_calendar, vol, m_dayCounter);
            GeneralizedBlackScholesProcess* pBlackScholesProcess = new BlackScholesProcess(Handle<Quote>(hSpot),
                Handle<YieldTermStructure>(yieldCurve->getRiskFreeTermStructure()),
                Handle<BlackVolTermStructure>(boost::shared_ptr<BlackVolTermStructure>(pVolTermStructure)));
            boost::shared_ptr<GeneralizedBlackScholesProcess> hBlackScholesProcess(pBlackScholesProcess);

            PricingEngine* pEuroEngine = new AnalyticDividendEuropeanEngine(hBlackScholesProcess);
            boost::shared_ptr<PricingEngine> hEuroEngine(pEuroEngine);
            pricingObject->getOption()->setPricingEngine(hEuroEngine);
            if (findImpliedVol) {
                vol = pricingObject->getOption()->impliedVolatility(price, hBlackScholesProcess);
            }
            return pricingObject;
        }

    private:
        DayCounter m_dayCounter;
        Calendar m_calendar;
        Date m_valueDate;
        CurveManager* m_curveManager;
        std::map<int64_t, EuroPricingObject*>  m_pricingObjects;


    };

Date parseDate(int64_t date) {
    unsigned int day = date % 100;
    date = date - day;
    unsigned int year = date / 10000;
    unsigned int month = (date - year * 10000) / 100;
    return Date(day, (Month)month, year);
}

CurveManager* g_curveManager;
EuroPricer* g_euroPricer;

JNIEXPORT jint JNICALL Java_com_lunar_pricing_QuantLibAdaptor_initialize(JNIEnv* env, jobject jobj, jlong valueDate) {
    std::cout << "[INFO] Initializing for " << valueDate << "..." << std::endl;
    Settings::instance().evaluationDate() = parseDate(valueDate);
    g_curveManager = new CurveManager();
    g_euroPricer = new EuroPricer(parseDate(valueDate), g_curveManager); 
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_pricing_QuantLibAdaptor_close(JNIEnv* env, jobject obj) {
    std::cout << "[INFO] Closing..." << std::endl;
    delete g_euroPricer;
    delete g_curveManager;
    g_euroPricer = 0;
    g_curveManager = 0;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_pricing_QuantLibAdaptor_createDividendCurve(JNIEnv* env, jobject obj, jlong secSid, jintArray amounts, jlongArray dates) {
    std::cout << "[INFO] Creating dividend curve for " << secSid << "..." << std::endl;
    DividendCurve* divCurve = g_curveManager->createDividendCurve(secSid);
    jsize len = env->GetArrayLength(amounts);
    jint* amountElements = env->GetIntArrayElements(amounts, 0);
    jlong* dateElements = env->GetLongArrayElements(dates, 0); 
    for (int i = 0; i < len; i++) {
        std::cout << "[INFO] Dividend for " << secSid << " on " << dateElements[i] << ", " << amountElements[i] / 1000.0 << std::endl;
        divCurve->addPoint(parseDate(dateElements[i]), amountElements[i] / 1000.0);
    }
    env->ReleaseIntArrayElements(amounts, amountElements, 0);
    env->ReleaseLongArrayElements(dates, dateElements, 0);
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_pricing_QuantLibAdaptor_createYieldCurve(JNIEnv* env, jobject obj, jint yieldRate) {
    std::cout << "[INFO] Creating yield curve..." << std::endl;
    g_curveManager->createYieldCurve(g_euroPricer->getValueDate(), (double)yieldRate / 1000.0, Actual365Fixed());
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_pricing_QuantLibAdaptor_addEuroOption(JNIEnv* env, jobject obj, jlong secSid, jlong undSid, jlong maturityDate, jboolean isCall, jint strike) {
    std::cout << "[INFO] Adding european option " << secSid << " - undsid: " << undSid << ", maturity: " << maturityDate << ", call/put: " << (isCall ? "call" : "put") << ", strike: " << strike / 1000.0 << std::endl;
    DividendCurve* divCurve = g_curveManager->getDividendCurve(undSid);
    g_euroPricer->addEuroPricingObject(secSid, parseDate(maturityDate), isCall ? Option::Call : Option::Put, strike / 1000.0, divCurve);
    return 0;
}

#define RESULT_SIZE 5
#define FAIR_INDEX 0
#define DELTA_INDEX 1
#define VEGA_INDEX 2
#define GAMMA_INDEX 3
#define IVOL_INDEX 4

JNIEXPORT jintArray JNICALL Java_com_lunar_pricing_QuantLibAdaptor_priceEuroOption(JNIEnv* env, jobject obj, jlong secSid, jlong undSid, jint spot, jint price) {
#ifdef _TRACELOG
    std::cout << "[INFO] Pricing european option " << secSid << " spot: " << spot << ", price: " << price << std::endl;
#endif    
    Volatility impliedVol;
    try {
        EuroPricingObject* pricingObject = g_euroPricer->priceEuroOption(secSid, undSid, spot / 1000.0, price / 1000.0, impliedVol);
        jintArray result = env->NewIntArray(RESULT_SIZE);
        if (result == 0) {
            //std::cout << "[INFO] Cannot price..." << std::endl;
            return 0;
        }
        jint resultArray[RESULT_SIZE];
        resultArray[FAIR_INDEX] = pricingObject->getOption()->NPV() * 1000000; // x1000 for price adjustment, x1000 for 3 decimal places
        resultArray[DELTA_INDEX] = pricingObject->getOption()->delta() * 100000; // x100000 for 5 decimal places
        resultArray[VEGA_INDEX] = pricingObject->getOption()->vega() * 100000; // x100000 for 5 decimal places
        resultArray[GAMMA_INDEX] = pricingObject->getOption()->gamma() * 100000; // x100000 for 5 decimal places
        resultArray[IVOL_INDEX] = impliedVol * 1000; // x 1000 for 3 decimal places
#ifdef _TRACELOG
        std::cout << "[INFO] Pricing result - fair:" << resultArray[FAIR_INDEX] << ", delta:" << resultArray[DELTA_INDEX] << ", vega:" << resultArray[VEGA_INDEX] << ", gamma:" << resultArray[GAMMA_INDEX] << ", ivol:" << resultArray[IVOL_INDEX] << std::endl;
#endif        
        env->SetIntArrayRegion(result, 0, RESULT_SIZE, resultArray);
        return result;
    }
    catch (const std::exception& e) {
        //std::cerr << "[ERROR] Exception caught while pricing " << secSid << ": " << e.what() << std::endl;
        return 0;
    }
}
