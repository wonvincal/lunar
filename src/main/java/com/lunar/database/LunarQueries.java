package com.lunar.database;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.StrategyTriggerType;

/*
 * Centralize place to put all database queries so if we switch database engine we don't have to seek all over the code for them
 * TODO shayan: should re-investigate using prepared statements and params instead of building the sql queries like this...
 */
public class LunarQueries {
    final static String GET_ALL_ACTIVE_ISSUERS_QUERY = "select distinct iss.sid, iss.short_name from instrument i " +
            "inner join instrument_type t on i.type_sid = t.sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join issuer iss on iss.sid = o.issuer_sid " +
            "where i.listed_date <= '%s' and o.maturity_date > '%s' and t.name = 'Warrant' and ut.name = 'Equity'";

    final static String GET_ALL_NOTES_FOR_ACTIVE_INSTRUMENTS_QUERY = "select distinct note.sid, i.symbol, note.create_time, " + 
    		"note.modify_time, note.is_deleted, note.is_archived, note.description from instrument i " +
            "inner join instrument_type t on i.type_sid = t.sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join note on note.entity_sid = i.sid " +
            "where i.listed_date <= '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";

    final static String GET_ALL_ACTIVE_INSTRUMENTS_QUERY = "select " +
    		"	i.symbol, " +
    		"	t.name as sec_type, " +
    		"	exchange_code, " +
    		"	case i.listed_date when '0000-00-00' then null else i.listed_date end listed_date, " +
    		"	i.lot_size, " +
    		"	ifnull(u.algo, i.algo) algo, " +
    		"	iss.short_name, " +    		
    		"	u.symbol as und_symbol, " +
    		"   cp.name, " +
    		"   os.name, " +
    		"	o.maturity_date, " +
    		"	o.strike * 1000 strike, " +
    		"	o.conversion_ratio * 1000 conversion_ratio, " +
    		"   oe.capped_strike * 1000 barrier " +
    		"from instrument i " +
    		"inner join instrument_type t on i.type_sid = t.sid " +
    		"inner join exchange e on e.sid = i.exchange_sid " +
    		"inner join region r on r.sid = e.region_sid " +
    		"left join instrument_option o on o.instrument_sid = i.sid " +
    		"left join instrument_exotic oe on oe.instrument_sid = i.sid " +
    		"left join issuer iss on iss.sid = o.issuer_sid " +
    		"left join option_style os on os.sid = o.option_style_sid " +
    		"left join callput_type cp on cp.sid = o.callput_sid " +
    		"left join instrument u on u.sid = o.underlying_sid " +
    		"left join instrument_type ut on ut.sid = u.type_sid " +
    		"where r.name = 'Hong Kong' and i.listed_date <= '%s' and " +
    		"(t.name in ('Equity', 'ETF', 'Index') or " +
    		"(t.name = 'Futures' and i.algo = 1) or " +
    		" (t.name = 'Warrant' and date_add(o.maturity_date, interval -30 day) > '%s' and u.listed_date <= '%s' and ut.name in ('Equity', 'ETF', 'Index')) or " +
    		" (t.name = 'Cbbc' and (isnull(i.delisted_date) or i.delisted_date > '%s') and date_add(o.maturity_date, interval -30 day) > '%s' and u.listed_date <= '%s' and ut.name in ('Equity', 'ETF', 'Index'))) " +
    		"order by isnull(u.sid) desc, sec_type, symbol";
    
    final static String GET_ALL_STRATEGIES_QUERY = "select sid, name from strategy";
    
    final static String GET_STRATEGY_SWITCHES_FOR_STRATEGIES_QUERY = "select s.name, ss.switch from strategy_switch ss " +
            "inner join strategy s on s.sid = ss.strategy_sid";
    
    final static String GET_STRATEGY_SWITCHES_FOR_ACTIVE_ISSUERS_QUERY = "select distinct iss.short_name, sis.switch from instrument i " +
            "inner join instrument_type t on i.type_sid = t.sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join issuer iss on iss.sid = o.issuer_sid " +
            "inner join strategy_issuer_switch sis on sis.issuer_sid = o.issuer_sid " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";
    
    final static String GET_STRATEGY_SWITCHES_FOR_ACTIVE_UNDERLYINGS_QUERY = "select distinct u.symbol, sus.switch from instrument i " +
            "inner join instrument_type t on i.type_sid = t.sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join strategy_underlying_switch sus on sus.underlying_sid = o.underlying_sid " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";
    
    final static String GET_STRATEGY_SWITCHES_FOR_ACTIVE_INSTRUMENTS_QUERY = "select i.symbol, sis.switch from  instrument i " +
            "inner join instrument_type t on i.type_sid = t.sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join strategy_instrument_switch sis on sis.instrument_sid = i.sid " +
            "inner join strategy s on s.sid = sis.strategy_sid " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";
    
    final static String GET_GLOBAL_WARRANT_PARAMS_QUERY = "select i.symbol, ifnull(s.name, 'SpeedArb1') from  instrument i " +
            "inner join instrument_type t on i.type_sid = t.sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "left join strategy_instrument_switch sis on sis.instrument_sid = i.sid " +
            "left join strategy s on s.sid = sis.strategy_sid " +                             
            "left join " +
            "(select max(d.date) date from " +
            "   (select date_add('%s', interval @row_number:=@row_number+1 day) date from interday, (select @row_number:=-10) t limit 9) d " +
            "   left join " +
            "   (select h.holiday_date from holiday h " +
            "       inner join calendar c on c.sid = h.calendar_sid " +
            "       inner join region r on r.sid = c.region_sid " +
            "       where r.iso_code = 'HK' " +
            "       and c.category = 'trading') h on d.date = h.holiday_date " +
            "   where h.holiday_date is null " +
            "       and dayofweek(d.date) != 1 " +
            "       and dayofweek(d.date) != 7) pd on 1 = 1 " +
            "left join interday inter on inter.instrument_sid = i.sid and inter.date = pd.date " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "order by inter.turnover desc";
    
    final static String GET_SPEEDARB_PARAMS_QUERY = "select " +
            "    p.default_size_threshold, " +
            "    p.default_velocity_threshold * 1000, " +
            "    p.default_mm_bid_size, " +
            "    p.default_mm_ask_size, " +
            "    p.default_order_size, " +
            "    p.default_profit_run_ticks, " +
            "    round(p.default_tick_sens_threshold * 1000), " +
            "    p.default_allowed_max_spread, " +
            "    p.default_ban_to_downvol_period * 1000, " +
            "    p.default_ban_to_tomake_period * 1000, " +
            "    p.default_spread_observe_period * 1000, " +
            "    p.default_to_making_quantity, " +
            "    p.default_to_making_period * 1000, " +
            "    p.default_sell_on_vol_down, " +
            "    p.default_stop_profit  * 1000, " +
            "    p.default_market_outlook, " +
            "    p.default_selling_ban_period * 1000, " +
            "    p.default_holding_period * 1000, " +
            "    p.default_wide_spread_allowance " +
            "from speedarb_param p " +
            "inner join strategy s on p.strategy_sid = s.sid " +
            "where s.name = '%s'";

    final static String GET_SPEEDARB_WARRANT_PARAMS_FOR_ALL_QUERY = "select " +
            "    i.symbol, " +
            "    ifnull(wp.mm_bid_size, p.default_mm_bid_size), " +
            "    ifnull(wp.mm_ask_size, p.default_mm_ask_size), " +
            "    ifnull(wp.order_size, ip.default_order_size), " +
            "    ifnull(wp.profit_run_ticks, ip.default_profit_run_ticks), " +
            "    ifnull(round(wp.tick_sensitivity_threshold * 1000), round(p.default_tick_sens_threshold * 1000)), " +
            "    ifnull(wp.allowed_max_spread, p.default_allowed_max_spread), " +
            "    ifnull(wp.ban_to_downvol_period * 1000, p.default_ban_to_downvol_period * 1000), " +
            "    ifnull(wp.ban_to_tomake_period * 1000, p.default_ban_to_tomake_period * 1000), " +
            "    ifnull(wp.spread_observe_period * 1000, p.default_spread_observe_period * 1000), " +
            "    ifnull(wp.to_making_quantity, p.default_to_making_quantity), " +
            "    ifnull(wp.to_making_period * 1000, p.default_to_making_period * 1000), " +
            "    ifnull(wp.sell_on_vol_down, ip.default_sell_on_vol_down), " +
            "    ifnull(wp.issuer_lag * 1000, ip.default_issuer_lag * 1000), " +
            "    ifnull(wp.stop_profit * 1000, p.default_stop_profit * 1000), " +
            "    ifnull(wp.market_outlook, p.default_market_outlook), " +
            "    ifnull(wp.selling_ban_period * 1000, p.default_selling_ban_period * 1000), " +
            "    ifnull(wp.holding_period * 1000, p.default_holding_period * 1000), " +
            "    ifnull(wp.allow_stoploss_flash_bid, ip.default_allow_stoploss_flash_bid), " +
            "    ifnull(wp.reset_stoploss_vol_down, ip.default_reset_stoploss_vol_down), " +
            "    ifnull(wp.issuer_lag_cap * 1000, ip.default_issuer_lag_cap * 1000), " +
            "    ifnull(wp.trigger_type, ip.default_trigger_type), " +
            "    ifnull(wp.pricing_mode, ip.default_pricing_mode), " +
            "    ifnull(wp.sell_non_issuer, ip.default_sell_non_issuer), " +
            "    ifnull(wp.tick_buffer, ip.default_tick_buffer), " +
            "    ifnull(wp.stoploss_tick_buffer, ip.default_stoploss_tick_buffer), " +
            "    ifnull(wp.wide_spread_allowance, p.default_wide_spread_allowance), " +
            "    ifnull(wp.sell_on_vol_down_ban_period * 1000, ip.default_sell_on_vol_down_ban_period * 1000), " +
            "    ifnull(wp.sell_at_quick_profit, ip.default_sell_at_quick_profit), " +
            "    ifnull(wp.use_hold_bid_ban, ip.default_use_hold_bid_ban), " +
            "    ifnull(wp.outstanding_threshold, ip.default_outstanding_threshold), " +            
            "    ifnull(wp.max_order_size, ip.default_max_order_size), " +
            "    ifnull(wp.order_size_increment, ip.default_order_size_increment), " +
            "    ifnull(wp.base_order_size, ip.default_base_order_size), " +       
            "    ifnull(wp.order_size_remainder, ip.default_order_size_remainder), " +
            "    ifnull(lp.delta * 100000, 0) " +
            "from instrument i " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +                             
            "inner join strategy s on s.name = '%s' " +
            "inner join speedarb_param p on p.strategy_sid = s.sid " +
            "inner join speedarb_issuer_param ip on ip.strategy_sid = ip.strategy_sid and ip.issuer_sid = o.issuer_sid " +
            "left join speedarb_warrant_param wp on wp.strategy_sid = s.sid and wp.instrument_sid = i.sid " +
            "left join live_pricing lp on lp.instrument_sid = wp.instrument_sid and lp.date = '%s' " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";
    
    final static String GET_SPEEDARB_ISSUER_PARAMS_FOR_ALL_QUERY = "select distinct " +
            "    iss.short_name, " +
            "    p.default_issuer_lag * 1000, " +
            "    p.default_allow_stoploss_flash_bid, " +
            "    p.default_reset_stoploss_vol_down, " +
            "    p.default_issuer_lag_cap * 1000, " +
            "    p.default_trigger_type, " +
            "    p.default_pricing_mode, " +
            "    p.default_profit_run_ticks, " +
            "    p.default_sell_non_issuer, " +
            "    p.default_tick_buffer, " +
            "    p.default_stoploss_tick_buffer, " +
            "    p.default_sell_on_vol_down, " +
            "    p.default_sell_on_vol_down_ban_period * 1000, " +
            "    p.default_sell_at_quick_profit, " +
            "    p.default_order_size, " +
            "    p.default_use_hold_bid_ban, " +
            "    p.default_outstanding_threshold, " +
            "    p.default_max_order_size, " +
            "    p.default_order_size_increment, " +
            "    p.default_base_order_size, " +
            "    p.default_order_size_remainder " +
            "from instrument i " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " + 
            "inner join issuer iss on iss.sid = o.issuer_sid " +
            "inner join strategy s on s.name = '%s' " +
            "inner join speedarb_issuer_param p on p.strategy_sid = s.sid and p.issuer_sid = o.issuer_sid " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";
            
    final static String GET_SPEEDARB_UNDERLYING_PARAMS_FOR_ALL_QUERY = "select distinct " +
            "    u.symbol, " +
            "    ifnull(up.size_threshold, p.default_size_threshold), " +
            "    ifnull(up.velocity_threshold * 1000, p.default_velocity_threshold * 1000) " +
            "from instrument i " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +                             
            "inner join strategy s on s.name = '%s' " +
            "inner join speedarb_param p on p.strategy_sid = s.sid " +
            "left join speedarb_underlying_param up on up.strategy_sid = s.sid and up.instrument_sid = u.sid " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";
    
    final static String GET_SPEEDARB_ISSUER_UND_PARAMS_FOR_ALL_QUERY = 
    		"select distinct issuer.short_name, u.symbol, ifnull(iup.und_trade_vol_threshold, p.default_und_trade_vol_threshold)" +
    		"from instrument i " +
    		"inner join instrument_type t on t.sid = i.type_sid " +
    		"inner join instrument_option o on o.instrument_sid = i.sid " +
    		"inner join issuer on issuer.sid = o.issuer_sid " +
    		"inner join instrument u on u.sid = o.underlying_sid " + 
    		"inner join instrument_type ut on ut.sid = u.type_sid " +
    		"inner join strategy s on s.name = '%s' " +
    		"inner join speedarb_issuer_param p on p.strategy_sid = s.sid and p.issuer_sid = o.issuer_sid " +
    		"left join speedarb_issuer_und_param iup on iup.strategy_sid = s.sid and iup.instrument_sid = u.sid and iup.issuer_sid = issuer.sid " +
    		"where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";
    
    final static String GET_SCOREBOARD_FOR_ALL_QUERY = "select " +
            "    i.symbol, " +
            "    ifnull(s.score, 500), " +
            "    ifnull(round(io.outstandings * 100000 / o.issue_size), 0), " +
            "    ifnull(round((-io.num_sell - io.num_buy) * 100000 / o.issue_size), 0), " +
            "    ifnull(io.outstandings, 0), " +
            "    ifnull(-io.num_sell - io.num_buy, 0), " +
            "    ifnull(round((-io.num_sell - io.num_buy) * prc.vega * .01 / o.conversion_ratio), 0), " +
            "    ifnull(prc.ivol * 1000, 0) " +
            "from instrument i " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +             
            "left join scoreboard s on s.instrument_sid = i.sid " +
            "left join " +
            "(select max(d.date) date from " +
            "   (select date_add('%s', interval @row_number:=@row_number+1 day) date from interday, (select @row_number:=-10) t limit 9) d " +
            "   left join " +
            "   (select h.holiday_date from holiday h " +
            "       inner join calendar c on c.sid = h.calendar_sid " +
            "       inner join region r on r.sid = c.region_sid " +
            "       where r.iso_code = 'HK' " +
            "       and c.category = 'trading') h on d.date = h.holiday_date " +
            "   where h.holiday_date is null " +
            "       and dayofweek(d.date) != 1 " +
            "       and dayofweek(d.date) != 7) pd on 1 = 1 " +
            "left join interday_outstanding io on io.instrument_sid = i.sid and io.date = pd.date " +            
            "left join live_pricing prc on prc.instrument_sid = i.sid and prc.date = pd.date " +
            "where i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF')";    
    
    final static String UPDATE_SCOREBOARD_QUERY = "insert into scoreboard (instrument_sid, score) " +
            "select i.sid, %d " + 
            "from instrument i " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "where i.symbol = '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "on duplicate key update " +
            "score = %d";

    final static String UPDATE_STRATEGY_SWITCHES_FOR_STRATEGIES_QUERY = "update strategy_switch set switch = %d where strategy_sid = (select sid from strategy where name = '%s')";
    
    final static String UPDATE_UNDERLYING_SWITCHES_FOR_STRATEGY_QUERY = "insert into strategy_underlying_switch (underlying_sid, switch) " +
            "select distinct u.sid, %d " +
            "from instrument u " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join instrument_option o on o.underlying_sid = u.sid " +                
            "inner join instrument i on i.sid = o.instrument_sid " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "where u.symbol = '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "on duplicate key update " +
            "switch = %d";
    
    final static String UPDATE_WARRANT_SWITCHES_FOR_STRATEGY_QUERY = "insert into strategy_instrument_switch (strategy_sid, instrument_sid, switch) " +
            "select s.sid, i.sid, %d " +
            "from strategy s " +
            "inner join instrument i on 1 = 1 " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "where s.name = 'SpeedArb1' and i.symbol = '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "on duplicate key update " +
            "switch = %d";

    final static String UPDATE_ISSUER_SWITCHES_FOR_STRATEGY_QUERY = "insert into strategy_issuer_switch (issuer_sid, switch) " +
            "select iss.sid, %d " +
            "from issuer iss " +
            "where iss.short_name = '%s' " +
            "on duplicate key update " +
            "switch = %d";
    
    final static String UPDATE_GLOBAL_PARAMS_QUERY = "insert into strategy_instrument_switch (strategy_sid, instrument_sid, switch) " +
            "select s.sid, i.sid, 0 " +
            "from strategy s " +
            "inner join instrument i on 1 = 1 " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "where s.name = '%s' and i.symbol = '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "on duplicate key ignore";
    
    final static String UPDATE_SPEEDARB_PARAMS_QUERY = "insert into speedarb_param (strategy_sid, " + 
            "default_size_threshold, default_velocity_threshold, default_mm_bid_size, default_mm_ask_size, default_order_size, " + 
            "default_profit_run_ticks, default_tick_sens_threshold, default_allowed_max_spread, " + 
            "default_ban_to_downvol_period, default_ban_to_tomake_period, default_spread_observe_period, default_to_making_quantity, default_to_making_period, " +
            "default_sell_on_vol_down, default_stop_profit, default_market_outlook, default_selling_ban_period, default_holding_period, default_wide_spread_allowance) " +
            "select s.sid, " + 
            "sp.default_size_threshold, sp.default_velocity_threshold, sp.default_mm_bid_size, sp.default_mm_ask_size, sp.default_order_size, " + 
            "sp.default_profit_run_ticks, sp.default_tick_sens_threshold, sp.default_allowed_max_spread, " +
            "sp.default_ban_to_downvol_period, sp.default_ban_to_tomake_period, sp.default_spread_observe_period, sp.default_to_making_quantity, sp.default_to_making_period, " +
            "sp.default_sell_on_vol_down, sp.default_stop_profit, sp.default_market_outlook, sp.default_selling_ban_period, sp.default_holding_period, sp.default_wide_spread_allowance " +
            "from strategy s " +
            "inner join strategy sa1 on sa1.name = 'SpeedArb1' " +
            "inner join speedarb_param sp on sp.strategy_sid = sa1.sid " +
            "where s.name = '%s' " +
            "on duplicate key update " +
            "strategy_sid = speedarb_param.strategy_sid";
    
    final static String UPDATE_SPEEDARB_UNDERLYING_PARAMS_QUERY = "insert into speedarb_underlying_param (strategy_sid, instrument_sid, size_threshold, velocity_threshold) " +
            "select distinct s.sid, u.sid, %d, %d / 1000 " +
            "from strategy s " +
            "inner join instrument u on 1 = 1 " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join instrument_option o on o.underlying_sid = u.sid " +                
            "inner join instrument i on i.sid = o.instrument_sid " +
            "inner join instrument_type t on t.sid = i.type_sid " +                
            "where s.name = '%s' and u.symbol = '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "on duplicate key update " +
            "size_threshold = %d, " +
            "velocity_threshold = %d / 1000";

    final static String UPDATE_SPEEDARB_ISSUER_PARAMS_QUERY = "insert into speedarb_issuer_param (strategy_sid, issuer_sid, default_issuer_lag, default_allow_stoploss_flash_bid, " + 
            "default_reset_stoploss_vol_down, default_issuer_lag_cap, default_trigger_type, default_pricing_mode, default_profit_run_ticks, default_sell_non_issuer, default_tick_buffer, default_stoploss_tick_buffer, default_sell_on_vol_down, default_sell_on_vol_down_ban_period, default_sell_at_quick_profit, default_order_size, default_use_hold_bid_ban, " +
            "default_outstanding_threshold, default_base_order_size, default_max_order_size, default_order_size_increment, default_order_size_remainder) " +
    		"select s.sid, iss.sid, %d / 1000, %d, %d, %d / 1000, %d, %d, %d, %d, %d, %d, %d, %d / 1000, %d, %d, %d, %d, %d, %d, %d, %d " +
            "from strategy s " +
            "inner join issuer iss on 1 = 1 " +
            "where s.name = '%s' and iss.short_name = '%s' " +
            "on duplicate key update " +
            "default_issuer_lag = %d / 1000, " +
            "default_allow_stoploss_flash_bid = %d, " +
            "default_reset_stoploss_vol_down = %d, " +
            "default_issuer_lag_cap = %d / 1000, " +
            "default_trigger_type = %d, " +
            "default_pricing_mode = %d, " +
            "default_profit_run_ticks = %d, " + 
            "default_sell_non_issuer = %d, " + 
            "default_tick_buffer = %d, " +
            "default_stoploss_tick_buffer = %d, " +
            "default_sell_on_vol_down = %d, " +
            "default_sell_on_vol_down_ban_period = %d / 1000, " +
            "default_sell_at_quick_profit = %d, " +
            "default_order_size = %d, " +
            "default_hold_bid_ban = %d, " +
            "default_outstanding_threshold = %d, " +             
            "default_base_order_size = %d, " + 
            "default_max_order_size = %d, " + 
            "default_order_size_increment = %d, " +
            "default_order_size_remainder = %d";

    final static String UPDATE_SPEEDARB_ISSUER_UND_PARAMS_QUERY = "insert into speedarb_issuer_und_param "
    		+ "(strategy_sid, issuer_sid, instrument_sid, und_trade_vol_threshold) " + 
            "select distinct s.sid, iss.sid, u.sid, %d " +
            "from strategy s " +
            "inner join instrument u on 1 = 1 " +
            "inner join instrument_option o on o.underlying_sid = u.sid " +                
            "inner join issuer iss on o.issuer_sid = iss.sid " +
            "where s.name = '%s' and iss.short_name = '%s' and u.symbol = '%s'" +
            "on duplicate key update " +
            "und_trade_vol_threshold = %d";
    
    final static String UPDATE_SPEEDARB_WARRANT_PARAMS_QUERY = "insert into speedarb_warrant_param (strategy_sid, instrument_sid, mm_bid_size, mm_ask_size, order_size, " +
            "profit_run_ticks, tick_sensitivity_threshold, allowed_max_spread, ban_to_downvol_period, ban_to_tomake_period, spread_observe_period, " +
            "to_making_quantity, to_making_period, sell_on_vol_down, issuer_lag, stop_profit, market_outlook, " +
            "selling_ban_period, holding_period, allow_stoploss_flash_bid, reset_stoploss_vol_down, " +
            "issuer_lag_cap, trigger_type, pricing_mode, sell_non_issuer, tick_buffer, stoploss_tick_buffer, wide_spread_allowance, sell_on_vol_down_ban_period, sell_at_quick_profit, use_hold_bid_ban, " +
            "outstanding_threshold, base_order_size, max_order_size, order_size_increment, order_size_remainder) " +
            "select s.sid, i.sid, %d, %d, %d, " + 
            "%d, %d / 1000, %d, %d / 1000, %d / 1000, %d / 1000, " +
            "%d, %d / 1000, %d, %d / 1000, round(%d / 1000), %d, " +
            "%d / 1000, %d / 1000, %d, %d, " +
            "%d / 1000, %d, %d, %d, %d, %d, %d, %d / 1000, %d, %d, %d, %d, %d, %d, %d " +
            "from strategy s " +
            "inner join instrument i on 1 = 1 " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "where s.name = '%s' and i.symbol = '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "on duplicate key update " + 
            "mm_bid_size = %d, " +
            "mm_ask_size = %d, " + 
            "order_size = %d, " + 
            "profit_run_ticks = %d, " + 
            "tick_sensitivity_threshold = %d / 1000, " +
            "allowed_max_spread = %d, " +
            "ban_to_downvol_period = %d / 1000, " +
            "ban_to_tomake_period = %d / 1000, " +
            "spread_observe_period = %d / 1000, " +
            "to_making_quantity = %d, " +
            "to_making_period = %d / 1000, " +
            "sell_on_vol_down = %d, " +
            //"issuer_lag = %d / 1000, " + - do not update issuer_lag
            "stop_profit = round(%d / 1000), " +
            "market_outlook = %d, " +
            "selling_ban_period = %d / 1000, " +
            "holding_period = %d / 1000, " +
            "allow_stoploss_flash_bid = %d, " +
            "reset_stoploss_vol_down = %d, " +
            "issuer_lag_cap = %d / 1000, " +
            "trigger_type = %d, " + 
            "pricing_mode = %d, " +
            "sell_non_issuer = %d, " + 
            "tick_buffer = %d, " +
            "stoploss_tick_buffer = %d, " +
            "wide_spread_allowance = %d, " +
            "sell_on_vol_down_ban_period = %d / 1000, " +
            "sell_at_quick_profit = %d, " +
            "use_hold_bid_ban = %d, " +
            "outstanding_threshold = %d, " +
            "base_order_size = %d, " +
            "max_order_size = %d, " +
            "order_size_increment = %d, " +
            "order_size_remainder = %d ";
    
    final static String UPDATE_GREEKS_QUERY = "insert into live_pricing (date, instrument_sid, ivol, delta, gamma, vega, bid_ivol, ask_ivol) " +
            "select '%s', i.sid, round(%d / 1000, 3), round(%d / 100000, 5), round(%d / 100000, 5), round(%d / 100000, 5), round(%d / 1000, 3), round(%d / 1000, 3) " +
            "from instrument i " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "inner join instrument_option o on o.instrument_sid = i.sid " +
            "inner join instrument u on u.sid = o.underlying_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "where i.symbol = '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "on duplicate key update " +
            " ivol = round(%d / 1000, 3), " +
            " delta = round(%d / 100000, 5), " +
            " gamma = round(%d / 100000, 5), " +
            " vega = round(%d / 100000, 5), " +
            " bid_ivol = round(%d / 1000, 3), " +
            " ask_ivol = round(%d / 1000, 3) ";
    
    final static String GET_DIVIDEND_CURVE_QUERY = "select distinct u.symbol, d.exdiv_date, d.amount * 1000 " +
            "from dividend_curve d " +
            "inner join instrument u on u.sid = d.instrument_sid " +
            "inner join instrument_type ut on ut.sid = u.type_sid " +
            "inner join instrument_option o on o.underlying_sid = u.sid " +                
            "inner join instrument i on i.sid = o.instrument_sid " +
            "inner join instrument_type t on t.sid = i.type_sid " +
            "where d.exdiv_date > '%s' and i.listed_date <= '%s' and date_add(o.maturity_date, interval -30 day) > '%s' and t.name = 'Warrant' and (ut.name = 'Equity' or ut.name = 'ETF') " +
            "order by u.symbol, d.exdiv_date";
    
    static public String getAllActiveIssuers(final String dateString) {
        return String.format(GET_ALL_ACTIVE_ISSUERS_QUERY, dateString, dateString);        
    }
    
	static public String getAllActiveInstruments(final String dateString) {
		return 	String.format(GET_ALL_ACTIVE_INSTRUMENTS_QUERY, dateString, dateString, dateString, dateString, dateString, dateString);		
	}

	static public String getAllNotesForActiveInstruments(final String dateString){
		return String.format(GET_ALL_NOTES_FOR_ACTIVE_INSTRUMENTS_QUERY, dateString);
	}
	
	static public String getAllStrategies() {
		return GET_ALL_STRATEGIES_QUERY;
	}

    static public String getStrategySwitchesForStrategies() {
        return GET_STRATEGY_SWITCHES_FOR_STRATEGIES_QUERY;
    }	
    
    static public String getStrategySwitchesForActiveIssuers(final String dateString) {
        return String.format(GET_STRATEGY_SWITCHES_FOR_ACTIVE_ISSUERS_QUERY, dateString, dateString);
    }       
	
    static public String getStrategySwitchesForActiveUnderlyings(final String dateString) {
        return String.format(GET_STRATEGY_SWITCHES_FOR_ACTIVE_UNDERLYINGS_QUERY, dateString, dateString);
    }	
	
	static public String getStrategySwitchesForActiveInstruments(final String dateString) {
		return String.format(GET_STRATEGY_SWITCHES_FOR_ACTIVE_INSTRUMENTS_QUERY, dateString, dateString);
	}
	
    static public String getGlobalWarrantParamsForAll(final String dateString) {
        // right now we store them in strategy_instrument_switch table...
        return String.format(GET_GLOBAL_WARRANT_PARAMS_QUERY, dateString, dateString, dateString);
    }   
    
	static public String getSpeedArbParams(final String strategyName) {
		return String.format(GET_SPEEDARB_PARAMS_QUERY, strategyName);							 
	}
	
	static public String getSpeedArbWarrantParamsForAll(final String dateString, final String strategyName) {
	    return String.format(GET_SPEEDARB_WARRANT_PARAMS_FOR_ALL_QUERY, strategyName, dateString, dateString, dateString);
	}
	
    static public String getSpeedArbUnderlyingParamsForAll(final String dateString, final String strategyName) {
        return String.format(GET_SPEEDARB_UNDERLYING_PARAMS_FOR_ALL_QUERY, strategyName, dateString, dateString);
    }

    static public String getSpeedArbIssuerParamsForAll(final String dateString, final String strategyName) {
        return String.format(GET_SPEEDARB_ISSUER_PARAMS_FOR_ALL_QUERY, strategyName, dateString, dateString);
    }

    static public String getSpeedArbIssuerUndParamsForAll(final String dateString, final String strategyName) {
        return String.format(GET_SPEEDARB_ISSUER_UND_PARAMS_FOR_ALL_QUERY, strategyName, dateString, dateString);
    }

    static public String getScoreBoardForAll(final String dateString) {
        return String.format(GET_SCOREBOARD_FOR_ALL_QUERY, dateString, dateString, dateString);
    }

    static public String updateScoreBoard(final String dateString, final String securityName, final int score) {
        return String.format(UPDATE_SCOREBOARD_QUERY, score, securityName, dateString, dateString, score);
    }
    
    static public String updateStrategySwitchesForStrategy(final String strategyName, final boolean onOff) {
        return String.format(UPDATE_STRATEGY_SWITCHES_FOR_STRATEGIES_QUERY,
                onOff ? 1 : 0, strategyName);
    }
    
    static public String updateUnderlyingSwitchesForStrategy(final String dateString, final String underlyingName, final boolean onOff) {
        final int onOffInt = onOff ? 1 : 0;
        return String.format(UPDATE_UNDERLYING_SWITCHES_FOR_STRATEGY_QUERY, onOffInt, underlyingName, dateString, dateString, onOffInt);
    }
    
    static public String updateWarrantSwitchesForStrategy(final String dateString, final String securityName, final boolean onOff) {
        final int onOffInt = onOff ? 1 : 0;
        return String.format(UPDATE_WARRANT_SWITCHES_FOR_STRATEGY_QUERY, onOffInt, securityName, dateString, dateString, onOffInt);
    }
    
    static public String updateIssuerSwitchesForStrategy(final String issuerName, final boolean onOff) {
        final int onOffInt = onOff ? 1 : 0;
        return String.format(UPDATE_ISSUER_SWITCHES_FOR_STRATEGY_QUERY, onOffInt, issuerName, onOffInt);
    }
    
    static public String updateGlobalWrtParams(final String dateString, final String securityName, final String strategyName) {
        return String.format(UPDATE_GLOBAL_PARAMS_QUERY, strategyName, securityName, dateString, dateString);
    }        
    
    static public String updateSpeedArbParams(final String strategyName) {
        return String.format(UPDATE_SPEEDARB_PARAMS_QUERY, strategyName);
    }

    static public String updateSpeedArbUndParams(final String dateString, final String strategyName, final String underlyingSymbol, final long sizeThreshold, final long velocityThreshold) {
        return String.format(UPDATE_SPEEDARB_UNDERLYING_PARAMS_QUERY, sizeThreshold, velocityThreshold, strategyName, underlyingSymbol, dateString, dateString, sizeThreshold, velocityThreshold);
    }

    static public String updateSpeedArbIssuerParams(final String dateString, final String strategyName, final String issuerName, final long defaultIssuerLag, final boolean defaultAllowStopLossOnFlashingBid, final boolean defaultResetStopLossOnVolDown,
            final long defaultIssuerLagCap, final StrategyTriggerType defaultTriggerType, final PricingMode defaultPricingMode, final int profitRunTicks, final boolean sellNonIssuer, final int tickBuffer, final int stopLossTickBuffer, final boolean defaultSellOnVolDown, final long defaultSellOnVolDownBanPeriod, final boolean sellAtQuickProfit, final int defaultOrderSize, final boolean defaultUseHoldBidBan, final long outstandingThreshold, final int baseOrderSize, final int maxOrderSize, final int orderSizeIncrement, final int orderSizeRemainder) {
        return String.format(UPDATE_SPEEDARB_ISSUER_PARAMS_QUERY,
                defaultIssuerLag, defaultAllowStopLossOnFlashingBid ? 1 : 0, defaultResetStopLossOnVolDown ? 1 : 0, defaultIssuerLagCap, defaultTriggerType.value(), defaultPricingMode.value(), profitRunTicks, sellNonIssuer ? 1 : 0, tickBuffer, stopLossTickBuffer, defaultSellOnVolDown ? 1 : 0, defaultSellOnVolDownBanPeriod, sellAtQuickProfit ? 1 : 0, defaultOrderSize, defaultUseHoldBidBan ? 1 : 0, outstandingThreshold, baseOrderSize, maxOrderSize, orderSizeIncrement, orderSizeRemainder,
                strategyName, issuerName, dateString, 
                defaultIssuerLag, defaultAllowStopLossOnFlashingBid ? 1 : 0, defaultResetStopLossOnVolDown ? 1 : 0, defaultIssuerLagCap, defaultTriggerType.value(), defaultPricingMode.value(), profitRunTicks, sellNonIssuer ? 1 : 0, tickBuffer, stopLossTickBuffer, defaultSellOnVolDown ? 1 : 0, defaultSellOnVolDownBanPeriod, sellAtQuickProfit ? 1 : 0, defaultOrderSize, defaultUseHoldBidBan ? 1 : 0, outstandingThreshold, baseOrderSize, maxOrderSize, orderSizeIncrement, orderSizeRemainder);
    }

    static public String updateSpeedArbIssuerUndParams(final String dateString, final String strategyName, final String issuerName, final String underlyingSymbol, final long undTradeVolThresholdfinal) {
        return String.format(UPDATE_SPEEDARB_ISSUER_UND_PARAMS_QUERY,
        		undTradeVolThresholdfinal,
                strategyName, issuerName, underlyingSymbol, 
                undTradeVolThresholdfinal);
    }

    static public String updateSpeedArbWrtParams(final String dateString, final String strategyName, final String securitySymbol, final long mmBidSize, final long mmAskSize, final long orderSize, final long profitRunTicks, final long tickSensitivityThreshold, 
            final long allowedMaxSpread, final long banToDownVolPeriod, final long banToTurnoverMakingPeriod, final long spreadObservationPeriod, final long turnoverMakingQuantity, final long turnoverMakingPeriod,
            final boolean sellOnVolDown, final long issuerLag, final long stopProfit, final MarketOutlookType outlook,
            final long sellingBanPeriod, final long holdingPeriod, final boolean allowStopLossOnFlashingBid, final boolean resetStopLossOnVolDown,
            final long issuerMaxLagCap, final StrategyTriggerType triggerType, final PricingMode pricingMode, final boolean sellNonIssuer, final int tickBuffer, final int stopLossTickBuffer, final int wideSpreadAllowance, final long sellOnVolDownBanPeriod, final boolean sellAtQuickProfit, final boolean useHoldBidBan,
            final long outstandingThreshold, final int baseOrderSize, final int maxOrderSize, final int orderSizeIncrement, final int orderSizeRemainder) {
        return String.format(UPDATE_SPEEDARB_WARRANT_PARAMS_QUERY, 
                mmBidSize, mmAskSize, orderSize, profitRunTicks, tickSensitivityThreshold, allowedMaxSpread, banToDownVolPeriod, banToTurnoverMakingPeriod, spreadObservationPeriod, turnoverMakingQuantity, turnoverMakingPeriod, sellOnVolDown ? 1 : 0, issuerLag, stopProfit, outlook.value(), sellingBanPeriod, holdingPeriod, allowStopLossOnFlashingBid ? 1 : 0, resetStopLossOnVolDown ? 1 : 0, issuerMaxLagCap, triggerType.value(), pricingMode.value(), sellNonIssuer ? 1 : 0, tickBuffer, stopLossTickBuffer, wideSpreadAllowance, sellOnVolDownBanPeriod, sellAtQuickProfit ? 1 : 0, useHoldBidBan ? 1 : 0, outstandingThreshold, baseOrderSize, maxOrderSize, orderSizeIncrement, orderSizeRemainder, 
                strategyName, securitySymbol, dateString, dateString,
                mmBidSize, mmAskSize, orderSize, profitRunTicks, tickSensitivityThreshold, allowedMaxSpread, banToDownVolPeriod, banToTurnoverMakingPeriod, spreadObservationPeriod, turnoverMakingQuantity, turnoverMakingPeriod, sellOnVolDown ? 1 : 0, /* -- do not update issuer lag -- issuerLag , */ stopProfit, outlook.value(), sellingBanPeriod, holdingPeriod, allowStopLossOnFlashingBid ? 1 : 0, resetStopLossOnVolDown ? 1 : 0, issuerMaxLagCap, triggerType.value(), pricingMode.value(), sellNonIssuer ? 1 : 0, tickBuffer, stopLossTickBuffer, wideSpreadAllowance, sellOnVolDownBanPeriod, sellAtQuickProfit ? 1 : 0, useHoldBidBan ? 1 : 0, outstandingThreshold, baseOrderSize, maxOrderSize, orderSizeIncrement, orderSizeRemainder);
    }
    
    static public String updateGreeks(final String dateString, final String securitySymbol, final int ivol, final int delta, final int gamma, final int vega, final int bidIVol, final int askIVol) {
        return String.format(UPDATE_GREEKS_QUERY, 
                dateString, ivol, delta, gamma, vega, bidIVol, askIVol, securitySymbol, dateString, dateString, ivol, delta, gamma, vega, bidIVol, askIVol);
    }
    
    static public String getDividendCurve(final String dateString) {
    	return String.format(GET_DIVIDEND_CURVE_QUERY, dateString, dateString, dateString);
    }
    
    final static String INSERT_ORDER_QUERY = "insert into order_quote (date, client_order_id, exchange_order_id, instrument_sid, order_type, quantity, side, tif, " +
            "is_algo_order, limit_price, stop_price, status, cumulative_qty, leaves_qty, parent_order_sid, order_reject_type, " +
            "reason, portfolio_sid, order_create_time, order_update_time) " +
            "select '%s', %d, '%s', i.sid, %d, %d, %d, %d, %d, %d / 1000, %d / 1000, %d, %d, %d, p.sid, %d, '%s', 1, '%s', '%s' " +
            "from instrument i " +
            "left join order_quote p on p.date = '%s' and p.client_order_id = %d " +
            "where i.symbol = '%s' and i.listed_date <= '%s' and (isnull(i.delisted_date) or i.delisted_date >= '%s')" +
            "on duplicate key update " +
            "   status = %d, " +
            "   exchange_order_id = '%s', " +
            "   cumulative_qty = %d, " +
            "   leaves_qty = %d, " +
            "   order_reject_type = %d, " +
            "   reason = '%s', " +
            "   order_update_time = '%s'";
            
    static public String insertOrUpdateOrder(final String dateString, int clientOrderId, String exchangeOrderId, String securitySymbol, int orderType, int quantity, int side,
            int tif, int isAlgoOrder, int limitPrice, int stopPrice, int status, int cumulativeQuantity, int leavesQuantity, int parentOrderId, int orderRejectType,
            String reason, final LocalDateTime createTime, final LocalDateTime updateTime) {
        final String createTimeString = createTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        final String updateTimeString = updateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return String.format(INSERT_ORDER_QUERY, dateString, clientOrderId, exchangeOrderId, orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, status,
                cumulativeQuantity, leavesQuantity, orderRejectType, reason, createTimeString, updateTimeString, dateString, parentOrderId, securitySymbol, dateString, dateString,
                status, exchangeOrderId, cumulativeQuantity, leavesQuantity, orderRejectType, reason, updateTimeString);
    }
    
    final static String INSERT_TRADE_QUERY = "insert into trade (date, trade_id, order_sid, instrument_sid, side, trade_status, " +
            "execution_id, price, quantity, cumulative_qty, leaves_qty, trade_create_time, trade_update_time) " +
            "select '%s', %d, o.sid, i.sid, %d, %d, '%s', %d / 1000, %d, %d, %d, '%s', '%s' " +
            "from instrument i " +
            "inner join order_quote o on o.date = '%s' and o.client_order_id = %d " +
            "where i.symbol = '%s' and i.listed_date <= '%s' and (isnull(i.delisted_date) or i.delisted_date >= '%s')" +
            "on duplicate key update " +
            "   trade_status = %d, " +
            "   execution_id = '%s', " +
            "   price = %d / 1000, " +
            "   quantity = %d, " +
            "   cumulative_qty = %d, " +
            "   leaves_qty = %d, " +
            "   trade_update_time = '%s'";
    static public String insertOrUpdateTrade(final String dateString, int tradeId, int orderId, String securitySymbol, int side, int tradeStatus,
            String executionId, int price, int quantity, int cumulativeQuantity, int leavesQuantity, final LocalDateTime createTime, final LocalDateTime updateTime) {
        final String createTimeString = createTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        final String updateTimeString = updateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return String.format(INSERT_TRADE_QUERY, dateString, tradeId, side, tradeStatus, executionId, price, quantity, cumulativeQuantity, leavesQuantity, createTimeString, updateTimeString,
                dateString, orderId, securitySymbol, dateString, dateString,
                tradeStatus, executionId, price, quantity, cumulativeQuantity, leavesQuantity, updateTimeString);
    }
    
    final static String GET_ALL_POSITIONS_QUERY = "select i.symbol, port.name, p.open_position from position p " +
            "inner join instrument i on i.sid = p.instrument_sid " +
            "inner join portfolio port on port.sid = p.portfolio_sid " +
            "where p.date = '%s'";
    
    static public String getAllOpenPositions(final String dateString) {
        return String.format(GET_ALL_POSITIONS_QUERY, dateString);
    }
    
    public static String SELECT_NOTE_QUERY = "select n.sid, i.symbol, n.entity_type_sid, n.create_time, n.modify_time, n.is_deleted, n.is_archived, n.description from note n inner join instrument i on i.sid = n.entity_sid where n.sid = ?";
    public static String INSERT_NOTE_QUERY = "insert into note (entity_sid, entity_type_sid, is_deleted, is_archived, description) "
    		+ "select i.sid, ?, ?, ?, ? from instrument i where i.symbol = ? and i.listed_date <= ? and (isnull(i.delisted_date) or i.delisted_date >= ?)";
    public static String UPDATE_NOTE_QUERY = "update note set is_deleted = ?, is_archived = ?, description = ? where sid = ?";
    
}
