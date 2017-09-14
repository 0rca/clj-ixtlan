var gulp = require("gulp");
var minifyCSS = require('gulp-minify-css');
var concat = require('gulp-concat');
var uglify = require('gulp-uglify');
var filesize = require('gulp-filesize');

gulp.task('minify-css', function() {
  gulp.src(['bower_components/foundation/css/normalize.css',
    'bower_components/foundation/css/foundation.min.css'])
    .pipe(concat('styles.css'))
    .pipe(filesize())
    .pipe(minifyCSS(opts))
    .pipe(filesize())
    .pipe(gulp.dest('./resources/public/css/'));
});

gulp.task('minify-js', function() {
  gulp.src([
    'out/release.js',
    'bower_components/jquery/dist/jquery.min.js',
    'bower_components/fastclick/lib/fastclick.js',
    'bower_components/foundation/js/foundation/foundation.js',
    // 'bower_components/foundation/js/foundation/foundation.dropdown.js',
    'bower_components/foundation/js/foundation/foundation.offcanvas.js'])
    .pipe(concat('application.js'))
    .pipe(filesize())
    .pipe(uglify())
    .pipe(filesize())
    .pipe(gulp.dest('./resources/public/js/'));
});

gulp.task("default", function(){

});
