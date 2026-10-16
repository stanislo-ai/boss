package com.stopfat.app.data

import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialization of workout plans and preferences for local storage. */
object PlanJson {

    fun toJson(plan: WorkoutPlan): String = JSONObject().apply {
        put("title", plan.title)
        put("summary", plan.summary)
        put("source", plan.source)
        put("location", plan.location.name)
        put("tips", JSONArray(plan.tips))
        put("days", JSONArray().apply {
            plan.days.forEach { d ->
                put(JSONObject().apply {
                    put("name", d.name)
                    put("focus", d.focus)
                    put("finisher", d.finisher)
                    put("exercises", JSONArray().apply {
                        d.exercises.forEach { e ->
                            put(JSONObject().apply {
                                put("name", e.name)
                                put("sets", e.sets)
                                put("reps", e.reps)
                                put("restSec", e.restSec)
                                put("tip", e.tip)
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun fromJson(json: String): WorkoutPlan? = runCatching {
        val o = JSONObject(json)
        val days = o.getJSONArray("days")
        val tips = o.optJSONArray("tips") ?: JSONArray()
        WorkoutPlan(
            title = o.optString("title"),
            summary = o.optString("summary"),
            source = o.optString("source"),
            location = runCatching { WorkoutLocation.valueOf(o.optString("location")) }.getOrDefault(WorkoutLocation.GYM),
            tips = (0 until tips.length()).map { tips.getString(it) },
            days = (0 until days.length()).map { i ->
                val d = days.getJSONObject(i)
                val ex = d.getJSONArray("exercises")
                WorkoutDay(
                    name = d.optString("name"),
                    focus = d.optString("focus"),
                    finisher = d.optString("finisher"),
                    exercises = (0 until ex.length()).map { j ->
                        val e = ex.getJSONObject(j)
                        PlannedExercise(e.optString("name"), e.optInt("sets", 3), e.optString("reps"), e.optInt("restSec", 60), e.optString("tip"))
                    },
                )
            },
        )
    }.getOrNull()

    fun prefsToJson(p: WorkoutPreferences): String = JSONObject().apply {
        put("location", p.location.name)
        put("level", p.level.name)
        put("goal", p.goal.name)
        put("days", p.daysPerWeek)
        put("minutes", p.minutes)
        put("equipment", p.equipment.name)
        put("notes", p.notes)
    }.toString()

    fun prefsFromJson(json: String?): WorkoutPreferences {
        if (json.isNullOrBlank()) return WorkoutPreferences()
        return runCatching {
            val o = JSONObject(json)
            WorkoutPreferences(
                location = WorkoutLocation.valueOf(o.getString("location")),
                level = WorkoutLevel.valueOf(o.getString("level")),
                goal = WorkoutGoal.valueOf(o.getString("goal")),
                daysPerWeek = o.getInt("days"),
                minutes = o.getInt("minutes"),
                equipment = HomeEquipment.valueOf(o.getString("equipment")),
                notes = o.optString("notes"),
            )
        }.getOrDefault(WorkoutPreferences())
    }
}
