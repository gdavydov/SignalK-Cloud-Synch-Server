/*
* ZDA codec
*
* Copyright 2014, Mikko Vesikkala
*
* Licensed under the Apache License, Version 2.0 (the "License")
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/

"use strict";

/*
=== ZDA - Time & Date ===

------------------------------------------------------------------------------
*******1         2  3  4    5  6  7
*******|         |  |  |    |  |  |
$--ZDA,hhmmss.ss,xx,xx,xxxx,xx,xx*hh<CR><LF>
------------------------------------------------------------------------------

Field Number:
1. UTC time (hours, minutes, seconds, may have fractional subsecond)
2. Day, 01 to 31
3. Month, 01 to 12
4. Year (4 digits)
5. Local zone description, 00 to +- 13 hours
6. Local zone minutes description, apply same sign as local hours
7. Checksum
*/

//var debug = require('debug')('signalk-parser-nmea0183/ZDA');
var utils = require('@signalk/nmea0183-utilities');
var moment = require('moment-timezone');

function isEmpty(mixed) {
  return typeof mixed !== 'string' && typeof mixed !== 'number' || typeof mixed === 'string' && mixed.trim() === '';
}

module.exports = function (parser, input) {
  try {
    var id = input.id,
        sentence = input.sentence,
        parts = input.parts,
        tags = input.tags;


    var empty = parts.reduce(function (e, val) {
      if (isEmpty(val)) {
        ++e;
      }
      return e;
    }, 0);

    if (empty > 3) {
      return Promise.resolve(null);
    }

    var time = parts[0] || '';
    var date = parts[1] + parts[2] + (parts[3] || '').slice(-2);

    var delta = {};
    if (time.length >= 6 && date.length === 6 && empty < 3) {
      var year = parts[3];
      var month = parts[2] - 1;
      var day = parts[1];
      var hour = (parts[0] || '').substring(0, 2);
      var minute = (parts[0] || '').substring(2, 4);
      var second = (parts[0] || '').substring(4, 6);
      var milliSecond = parts[0].substring(4) % second * 1000;
      var d = new Date(Date.UTC(year, month, day, hour, minute, second, milliSecond));
      var ts = d.toISOString();
      delta = {
        updates: [{
          source: tags.source,
          timestamp: tags.timestamp,
          values: [{
            "path": "navigation.datetime",
            "value": ts
          }]
        }]
      };
    }

    var toRemove = [];

    delta.updates[0].values.forEach(function (update, index) {
      if (typeof update.value === 'undefined' || update.value === null || typeof update.value === 'string' && update.value.trim() === '' || typeof update.value !== 'string' && isNaN(update.value)) {
        toRemove.push(index);
      }
    });

    if (toRemove.length > 0) {
      toRemove.forEach(function (index) {
        delta.updates[0].values.splice(index, 1);
      });
    }

    return Promise.resolve({ delta: delta });
  } catch (e) {
    debug('Try/catch failed: ' + e.message);
    return Promise.reject(e);
  }
};