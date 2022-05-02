package com.lunar.pricing;

import com.lunar.entity.SidManager;

public interface CurvesLoader {
    public interface Loadable<T> {
        void loadInfo(T obj);
    }
    
    public interface LoadableWithKey<K, T> {
        void loadInfo(K key, T obj);
    }
    
    void loadDividendCurve(LoadableWithKey<String, DividendCurve> loadable, final SidManager<String> securitySidManager) throws Exception;
}
