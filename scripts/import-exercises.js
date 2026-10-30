#!/usr/bin/env node
/**
 * Import script: free-exercise-db JSON -> Room entities (Kotlin file)
 * Usage: node import-exercises.js <path-to-exercises.json> <output-dir>
 * 
 * This generates a Kotlin file with prepopulated Exercise objects
 * that can be included in the app for offline-first database initialization.
 */

const fs = require('fs');
const path = require('path');

function toKotlinString(str) {
    if (!str) return 'null';
    return '"' + str
        .replace(/\\/g, '\\\\')
        .replace(/"/g, '\\"')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '\\r')
        .replace(/\t/g, '\\t') + '"';
}

function toKotlinList(list) {
    if (!list || list.length === 0) return 'emptyList()';
    return 'listOf(' + list.map(toKotlinString).join(', ') + ')';
}

function toKotlinBoolean(bool) {
    return bool ? 'true' : 'false';
}

function main() {
    const args = process.argv.slice(2);
    if (args.length < 2) {
        console.error('Usage: node import-exercises.js <exercises.json> <output-dir>');
        process.exit(1);
    }

    const inputPath = args[0];
    const outputDir = args[1];

    console.log(`Reading ${inputPath}...`);
    const raw = fs.readFileSync(inputPath, 'utf-8');
    const data = JSON.parse(raw);
    
    // Handle both array and {exercises: []} formats
    const exercises = Array.isArray(data) ? data : data.exercises;
    console.log(`Found ${exercises.length} exercises`);

    const outputLines = [
        'package com.treinos.shared.data',
        '',
        'import com.treinos.shared.data.Exercise',
        '',
        '/**',
        ' * Auto-generated from free-exercise-db',
        ' * DO NOT EDIT MANUALLY',
        ' */',
        'object ExerciseCatalog {',
        '    val allExercises: List<Exercise> = listOf('
    ];

    for (const ex of exercises) {
        outputLines.push('        Exercise(');
        outputLines.push(`            id = ${toKotlinString(ex.id)},`);
        outputLines.push(`            name = ${toKotlinString(ex.name)},`);
        outputLines.push(`            force = ${toKotlinString(ex.force)},`);
        outputLines.push(`            level = ${toKotlinString(ex.level)},`);
        outputLines.push(`            mechanic = ${toKotlinString(ex.mechanic)},`);
        outputLines.push(`            equipment = ${toKotlinString(ex.equipment)},`);
        outputLines.push(`            primaryMuscles = ${toKotlinList(ex.primaryMuscles)},`);
        outputLines.push(`            secondaryMuscles = ${toKotlinList(ex.secondaryMuscles)},`);
        outputLines.push(`            instructions = ${toKotlinList(ex.instructions)},`);
        outputLines.push(`            category = ${toKotlinString(ex.category)},`);
        outputLines.push(`            images = ${toKotlinList(ex.images)},`);
        outputLines.push(`            namePt = null,`);
        outputLines.push(`            isFavorite = false,`);
        outputLines.push(`            lastUsedTimestamp = 0L`);
        outputLines.push('        ),');
    }

    outputLines.push('    )');
    outputLines.push('}');

    const outputPath = path.join(outputDir, 'ExerciseCatalog.kt');
    fs.writeFileSync(outputPath, outputLines.join('\n') + '\n');
    console.log(`Generated ${outputPath} with ${exercises.length} exercises`);
}

main();