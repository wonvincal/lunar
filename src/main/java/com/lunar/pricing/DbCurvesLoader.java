package com.lunar.pricing;

import java.sql.ResultSet;
import java.time.LocalDate;

import com.lunar.core.SystemClock;
import com.lunar.database.DbConnection;
import com.lunar.database.LunarQueries;
import com.lunar.entity.SidManager;

public class DbCurvesLoader implements CurvesLoader {
    final SystemClock m_systemClock;
    private final DbConnection m_connection;    

    public DbCurvesLoader(final SystemClock systemClock, final DbConnection connection) {
        m_systemClock = systemClock;
        m_connection = connection;
    }
    
	@Override
	public void loadDividendCurve(final LoadableWithKey<String, DividendCurve> loadable, final SidManager<String> securitySidManager) throws Exception {
        final ResultSet rs = m_connection.executeQuery(LunarQueries.getDividendCurve(m_systemClock.dateString()));
        try {
            if (rs != null) {
            	String prevCode = null;
                long secSid;
            	DividendCurve curve = null;
                while (rs.next()) {
                    final String secCode = rs.getString(1);
        			final LocalDate date = rs.getDate(2).toLocalDate();
        			final int amount = rs.getInt(3);
                    if (!secCode.equals(prevCode)) {
                    	if (curve != null) {
                    		loadable.loadInfo(prevCode, curve);
                    	}
                    	secSid = securitySidManager.getSidForKey(secCode);
                    	curve = new DividendCurve(secSid);
                    }
        			curve.addPoint(date, amount);
        			prevCode = secCode;
                }
                if (curve != null) {
            		loadable.loadInfo(prevCode, curve);
                }                
            }
        }
        finally {
            m_connection.closeQuery(rs);
        }   
		
	}

}
