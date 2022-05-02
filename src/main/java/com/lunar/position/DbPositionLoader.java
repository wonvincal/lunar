package com.lunar.position;

import java.sql.ResultSet;

import com.lunar.core.SystemClock;
import com.lunar.database.DbConnection;
import com.lunar.database.LunarQueries;
import com.lunar.entity.SidManager;

public class DbPositionLoader implements PositionLoader {
    private final SystemClock systemClock;
    private final DbConnection connection;

    public DbPositionLoader(final SystemClock systemClock, final DbConnection connection) {
        this.systemClock = systemClock;
        this.connection = connection;
    }
        
    @Override
    public void loadPositions(SidManager<String> securitySidManager, Loadable<Position> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getAllOpenPositions(systemClock.dateString()));
        try {
            if (rs != null) {
                while (rs.next()) {                    
                    final String secSymbol = rs.getString(1);
                    final long position = rs.getLong(3);
                    final long secSid = securitySidManager.getSidForKey(secSymbol);
                    loadable.loadInfo(Position.of(secSid, position));
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }                
    }

}
