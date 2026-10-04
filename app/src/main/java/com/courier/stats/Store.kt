package com.courier.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val ORDER_PRICE = 200
const val BONUS_PRICE = 50

data class Shift(val start: Long, val end: Long, val orders: Int, val bonuses: Int = 0) {
    val income get() = orders * ORDER_PRICE + bonuses * BONUS_PRICE
    val hours get() = (end - start) / 3_600_000.0
}

object Store {
    private fun p(c: Context) = c.getSharedPreferences("courier", Context.MODE_PRIVATE)
    fun active(c: Context) = p(c).getLong("active", 0L)
    fun setActive(c: Context, v: Long) = p(c).edit().putLong("active", v).apply()
    fun shifts(c: Context): List<Shift> {
        val a = JSONArray(p(c).getString("shifts", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it); Shift(o.getLong("s"), o.getLong("e"), o.getInt("n"), o.optInt("b", 0))
        }
    }
    fun remove(c: Context, start: Long) {
        val a = JSONArray(p(c).getString("shifts", "[]")); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getLong("s") != start) n.put(a.getJSONObject(i))
        p(c).edit().putString("shifts", n.toString()).apply()
    }
    fun add(c: Context, s: Shift) {
        val a = JSONArray(p(c).getString("shifts", "[]"))
        a.put(JSONObject().put("s", s.start).put("e", s.end).put("n", s.orders).put("b", s.bonuses))
        p(c).edit().putString("shifts", a.toString()).apply()
    }
}
