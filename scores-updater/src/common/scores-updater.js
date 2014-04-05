// ==UserScript==
// @name ScoresUpdater
// @include *baseball.fantasysports.yahoo.com/b1/25891*
// @require jquery-1.11.0.js
// ==/UserScript==

var $ = window.$.noConflict(true);

    var teams;

    kango.xhr.send({
        method: "GET",
        url: "http://nickgieschen.github.io/operationshutdown/points-update-2014.js",
        async: true,
        contentType: "json"}, function(data){
            if (data.status == 200){
                console.log(data);
                teams = data.response;
            }
        });

    var interval = setInterval(function(){

        var rows = $("#standingstable > tbody > tr");

        if (rows.length == 18 && teams){
                clearInterval(interval);

                var updates = [];
                var highestScore = 0;

                rows.each(function(i, item){
                    var cells = $(item).children("td");
                    teamNameEl = $(cells[1]).children().eq(1);
                    pointsEl = $(cells[2]).find(":first-child");
                    pointsBackEl = $(cells[4]);
                    oldScore = parseInt(pointsEl.text());
                    teamName = teamNameEl.text();
                    newScore = Math.round((oldScore + teams[teamName]) * 100) / 100;
                    if (newScore > highestScore) highestScore = newScore;
                    updates[updates.length] = {
                        tr:  $(item).clone(true, true),
                        oldScore: oldScore,
                        newScore: newScore
                    };
                });

                updates.sort(function(left, right){
                    if (left.newScore > right.newScore)
                        return -1;
                    if (left.newScore < right.newScore)
                        return 1;
                    return 0;
                });

                for (var i=0; i<rows.length; i++){
                    var update = updates[i];
                    var newRow = update.tr;
                    $(rows[i]).replaceWith(newRow);
                    var cells = newRow[0].cells;
                    console.log(newRow);
                    $(cells[0]).find(":first-child").text(i+1);
                    $(cells[2]).find(":first-child").text(update.newScore + " (" + update.oldScore + ")");
                    var pointsBack = highestScore - update.newScore;
                    $(cells[4]).find(":first-child").text((pointsBack == 0) ? "-" : Math.round(pointsBack * 100) / 100);
                }
        }
    }, 200);

