// Copyright 2010 Google

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 */


/**
 * The Google IO Mall Map
 * @constructor
 */
function GoogleIo() {
  this.center_ = new google.maps.LatLng(37.78313383211993,
      -122.40394949913025);

  this.mapDiv_ = document.getElementById(this.MAP_ID);

  this.map_ = new google.maps.Map(this.mapDiv_, {
    zoom: 18,
    center: this.center_,
    navigationControl: true,
    mapTypeControl: false,
    scaleControl: true,
    mapTypeId: google.maps.MapTypeId.HYBRID
  });

  this.infoWindow_ = new google.maps.InfoWindow({
    maxWidth: 330
  });

  this.isMobile_();

  var that = this;
  google.maps.event.addListenerOnce(this.map_, 'tilesloaded', function() {
    that.enableToolbox_();
    if (that.hasMapContainer_()) {
      MAP_CONTAINER.onMapReady();
    }
  });

  this.addLevelMarkers_();

  if (!document.location.hash) {
    this.showLevel(this.LEVELS_[0], true);
  }

  this.checkLocalStorage_();

  this.loadMallMapContent_();

  if (!this.hasMapContainer_()) {
    this.initLocationHashWatcher_();
  }
}


/**
 * @type {string}
 */
GoogleIo.prototype.MAP_ID = 'map-canvas';


/**
 * @type {Array.<string>}
 * @private
 */
GoogleIo.prototype.LEVELS_ = ['1', '2', '3'];


/**
 * @type {number?}
 * @private
 */
GoogleIo.prototype.currentLevel_ = null;


/**
 * @type {google.maps.LatLng?}
 * @private
 */
GoogleIo.prototype.userPosition_ = null;

/**
 * @type {number?}
 * @private
 */
GoogleIo.prototype.userLevel_ = null;


/**
 * @type {string?}
 * @private
 */
GoogleIo.prototype.currentHash_ = null;


/**
 * @type {string}
 * @private
 */
GoogleIo.prototype.SESSION_BASE_ =
  'http://code.google.com/events/io/2010/sessions/';


/**
 * @type {Object}
 * @private
 */
GoogleIo.prototype.markers_ = {
  'LEVEL1': [],
  'LEVEL2': [],
  'LEVEL3': []
};

/**
 * @type {number}
 * @private
 */
GoogleIo.prototype.markerIndex_ = 10;

/**
 * @type {number}
 * @private
 */
GoogleIo.prototype.userIndex_ = 11;

/**
 * @type {string}
 * @private
 */
GoogleIo.prototype.BASE_TILE_URL_ =
  'http://www.gstatic.com/io2010maps/tiles/2/';


/**
 * @type {string}
 * @private
 */
GoogleIo.prototype.TILE_TEMPLATE_URL_ = GoogleIo.prototype.BASE_TILE_URL_ +
    'L{L}_{Z}_{X}_{Y}.png';


/**
 * @type {string}
 * @private
 */
GoogleIo.prototype.SIMPLE_TILE_TEMPLATE_URL_ =
    GoogleIo.prototype.BASE_TILE_URL_ + '{Z}_{X}_{Y}.png';


/**
 * @type {number}
 * @private
 */
GoogleIo.prototype.MIN_RESOLUTION_ = 16;


/**
 * @type {number}
 * @private
 */
GoogleIo.prototype.MAX_RESOLUTION_ = 20;


/**
 * @type {boolean}
 * @private
 */
GoogleIo.prototype.ready_ = false;


/**
 * @type {Object}
 * @private
 */
GoogleIo.prototype.RESOLUTION_BOUNDS_ = {
  16: [[10484, 10485], [25328, 25329]],
  17: [[20969, 20970], [50657, 50658]],
  18: [[41939, 41940], [101315, 101317]],
  19: [[83878, 83881], [202631, 202634]],
  20: [[167757, 167763], [405263, 405269]]
};


/**
 * Initialise the location hash watcher.
 *
 * @private
 */
GoogleIo.prototype.initLocationHashWatcher_ = function() {
  var that = this;

  window.setInterval(function() {
    that.checkLocationHash_();
  }, 100);
};


/**
 * Checks if a MAP_CONTAINER object exists
 *
 * @return {boolean} Whether the object exists or not.
 * @private
 */
GoogleIo.prototype.hasMapContainer_ = function() {
  return typeof(window['MAP_CONTAINER']) !== 'undefined';
};


/**
 * Checks the local storage
 * @private
 */
GoogleIo.prototype.checkLocalStorage_ = function() {
  var sandboxItems = this.getFromLocalStorage_('sandbox');
  if (sandboxItems) {
    this.sandboxItems_ = sandboxItems;
  }

  var sessionItems = this.getFromLocalStorage_('sessions');
  if (sessionItems) {
    this.sessionItems_ = sessionItems;
  }
};


/**
 * Get a item from the local storage
 * @param {string} key the key of the item to get.
 * @return {Object|string|null} The item from the local storage.
 * @private
 */
GoogleIo.prototype.getFromLocalStorage_ = function(key) {
  if (typeof(window['localStorage']) !== 'undefined' &&
      typeof(window['JSON']) !== 'undefined' && localStorage && JSON) {
    var value = localStorage.getItem(key);

    // Crude hack to see if its a json object string.
    // Suits our purpose
    if (value && value.indexOf('{') !== -1) {
      return JSON.parse(value);
    }
    return value;
  }
};


/**
 * Adds data to the local storage
 * @param {string} key The key of the item to store.
 * @param {string|Object} value The item to store in the local store.
 * @private
 */
GoogleIo.prototype.addToLocalStorage_ = function(key, value) {
  if (typeof(window['localStorage']) !== 'undefined' &&
      typeof(window['JSON']) !== 'undefined' && localStorage && JSON) {
    if (typeof(value) == 'object') {
      value = JSON.stringify(value);
    }
    localStorage.setItem(key, value);
  }
};


/**
 * Detects if mobile and some mobile specific events.
 * @private
 */
GoogleIo.prototype.isMobile_ = function() {
  var logo = document.getElementById('io-logo');

  if (logo && logo.offsetWidth == 265) {
    this.setContentHeight();
    var that = this;

    this.map_.setOptions({
      scaleControl: false
    });

    google.maps.event.addDomListener(window, 'resize', function() {
      that.setContentHeight();
    });
    google.maps.event.addListener(this.map_, 'click', function() {
      that.infoWindow_.close();
    });

    google.maps.event.addDomListenerOnce(window, 'load', function() {
      window.setTimeout(function() {
        window.scrollTo(0, 1);
      }, 200);
    });
  }
};


/**
 * Converts a name to id
 *
 * @param {string} name The name to convert.
 * @return {string} id A nice id.
 * @private
 */
GoogleIo.prototype.nameToId_ = function(name) {
  return name.toLowerCase().replace(/[^a-z0-9-_]/ig, '');
};


/**
 *  Sets the height of the content area so that the map takes up all the
 *  available height.
 */
GoogleIo.prototype.setContentHeight = function() {
  var height = document.body.clientHeight;
  var topHeight = document.getElementById('top').clientHeight;
  var bottomHeight = document.getElementById('bottom').clientHeight;
  var mapCanvas = document.getElementById('map-canvas');

  mapCanvas.style.height = (height - topHeight - bottomHeight + 30) + 'px';
  google.maps.event.trigger(this.map_, 'resize');
};


/**
 * Check the location hash and update if neeed.
 *
 * @param {boolean} force To force the location hash update.
 * @private
 */
GoogleIo.prototype.checkLocationHash_ = function(force) {
  var hash = document.location.hash;

  if (force || this.currentHash_ != hash) {
    this.currentHash_ = hash;

    var match = hash.match(/level(\d)(?:\:([\w-]+))?/);

    if (match && match[1]) {
      if (force || this.currentLevel_ != match[1]) {
        this.showLevel(match[1], false, force);
      }

      if (match[2] && this.mallMapContent_) {
        for (var i = 0, content; content = this.mallMapContent_[i]; i++) {
          if (content.id == match[2]) {
            this.openContentInfo(content);
            break;
          }
        }
      } else {
        this.closeInfoWindow();
      }
    }
  }
};


/**
 * Close the info window
 */
GoogleIo.prototype.closeInfoWindow = function() {
  this.infoWindow_.close();
};


/**
 * Trim whitespace from a string
 *
 * @param {string} s The string to trim.
 * @return {string} A trimmed string.
 */
GoogleIo.prototype.trim = function(s) {
  return s.replace(/(^\s+)|(\s+$)/g, '');
};


/**
 *  Creates the markers for each room and sandbox.
 *  @private
 */
GoogleIo.prototype.addLevelMarkers_ = function() {
  for (var i = 0, level; level = this.LEVELS_[i]; i++) {
    var id = 'LEVEL' + level;
    var rooms = this.LOCATIONS[id];

    for (var room in rooms) {
      var info = rooms[room];

      info.id = room;
      if (info.type) {
        var marker = this.createContentMarker_(info);

        this.markers_[id].push(marker);
      }
    }
  }
};


/**
 * Loads the Mall maps content.
 *
 * @private
 */
GoogleIo.prototype.loadMallMapContent_ = function() {
  // Initiate a JSONP request.
  var that = this;

  // Add a exposed call back function
  window['loadSessionsCallback'] = function(json) {
    that.loadSessionsCallback(json);
  }

  // Add a exposed call back function
  window['loadSandboxCallback'] = function(json) {
    that.loadSandboxCallback(json);
  }

  var key = 't0bDxnEqbFO4XuYpkA070Nw';
  var worksheetIDs = {
    'sessions': 'od6',
    'sandbox': 'od5'
  };
  var jsonpUrl = 'http://spreadsheets.google.com/feeds/list/' +
      key + '/' + worksheetIDs.sessions + '/public/values' +
      '?alt=json-in-script&callback=loadSessionsCallback';
  var script = document.createElement('script');

  script.setAttribute('src', jsonpUrl);
  script.setAttribute('type', 'text/javascript');
  document.documentElement.firstChild.appendChild(script);

  jsonpUrl = 'http://spreadsheets.google.com/feeds/list/' +
      key + '/' + worksheetIDs.sandbox + '/public/values' +
      '?alt=json-in-script&callback=loadSandboxCallback';
  script = document.createElement('script');

  script.setAttribute('src', jsonpUrl);
  script.setAttribute('type', 'text/javascript');
  document.documentElement.firstChild.appendChild(script);
};


/**
 * Converts a time to 24 hour time.
 * @param {string} time A time string in 12 hour format.
 * @return {string} A time in 24 hour format.
 * @private
 */
GoogleIo.prototype.convertTo24Hour_ = function(time) {
  var pm = time.indexOf('pm') != -1;

  time = time.replace(/[am|pm]/ig, '');
  if (pm) {
    var bits = time.split(':');
    var hr = parseInt(bits[0], 10);

    if (hr < 12) {
      time = (hr + 12) + ':' + bits[1];
    }
  }

  return time;
};


/**
 * The callback for when the map json is loaded.
 *
 * @param {Object} json The json object with the map content.
 */
GoogleIo.prototype.loadSandboxCallback = function(json) {
  var updated = json.feed.updated.$t;
  var lastUpdated = this.getFromLocalStorage_('sandboxUpdated');
  var sandbox = this.getFromLocalStorage_('sandbox');

  if (updated == lastUpdated && sandbox) {
    return;
  }

  var contentItems = [];
  var entries = json.feed.entry;

  for (var i = 0, entry; entry = entries[i]; i++) {
    contentItems.push({
      companyName: entry.gsx$companyname.$t,
      companyDesc: entry.gsx$companydesc.$t,
      companyUrl: entry.gsx$companyurl.$t,
      pod: this.nameToId_(entry.gsx$companypod.$t)
    });
  }

  this.sandboxItems_ = contentItems;
  this.addToLocalStorage_('sandbox', contentItems);
  this.addToLocalStorage_('sandboxUpdated', updated);

  this.checkLocationHash_(true);
};


/**
 * The callback for when the map json is loaded.
 *
 * @param {Object} json The json object with the map content.
 */
GoogleIo.prototype.loadSessionsCallback = function(json) {
  var updated = json.feed.updated.$t;
  var lastUpdated = this.getFromLocalStorage_('sessionsUpdated');
  var sessions = this.getFromLocalStorage_('sessions');

  if (updated == lastUpdated && sessions) {
    return;
  }

  var contentItems = [];
  var entries = json.feed.entry;

  for (var i = 0, entry; entry = entries[i]; i++) {
    var item = {
      sessionDate: entry.gsx$sessiondate.$t,
      sessionTime: entry.gsx$sessiontime.$t,
      room: this.nameToId_(entry.gsx$room.$t),
      product: entry.gsx$product.$t,
      track: entry.gsx$track.$t,
      waveId: entry.gsx$waveid.$t,
      sessionTitle: entry.gsx$sessiontitle.$t,
      sessionLink: entry.gsx$sessionlink.$t,
      sessionSpeakers: entry.gsx$sessionspeakers.$t,
      sessionAbstract: entry.gsx$sessionabstract.$t
    };

    if (item.sessionDate.indexOf('19') != -1) {
      item.sessionDay = 19;
    } else {
      item.sessionDay = 20;
    }

    var timeParts = item.sessionTime.split('-');

    item.sessionStart = this.convertTo24Hour_(timeParts[0]);
    item.sessionEnd = this.convertTo24Hour_(timeParts[1]);

    contentItems.push(item);
  }

  this.addToLocalStorage_('sessions', contentItems);
  this.addToLocalStorage_('sessionsUpdated', updated);
  this.sessionItems_ = contentItems;
  this.checkLocationHash_(true);
};


/**
 * Create a marker with the content item's correct icon.
 *
 * @param {Object} item The content item for the marker.
 * @return {google.maps.Marker} The new marker.
 * @private
 */
GoogleIo.prototype.createContentMarker_ = function(item) {
  var image = new google.maps.MarkerImage(
      'images/marker-' + item.icon + '.png',
      new google.maps.Size(30, 28),
      new google.maps.Point(0, 0),
      new google.maps.Point(13, 26));

  var shadow = new google.maps.MarkerImage(
      'images/marker-shadow.png',
      new google.maps.Size(30, 28),
      new google.maps.Point(0, 0),
      new google.maps.Point(13, 26));

  var latLng = new google.maps.LatLng(item.lat, item.lng);
  var marker = new google.maps.Marker({
    position: latLng,
    shadow: shadow,
    icon: image,
    title: item.title,
    zIndex: this.markerIndex_
  });
  var that = this;

  google.maps.event.addListener(marker, 'click', function() {
    that.openContentInfo(item);
  });

  return marker;
};


/**
 * Open a info window for a content item.
 *
 * @param {Object} item A content item that was read from the IO speradher that
 *     contains a type, id and title.
 */
GoogleIo.prototype.openContentInfo = function(item) {
  if (this.hasMapContainer_()) {
    MAP_CONTAINER.openContentInfo(item.id);
    return;
  }

  var now = new Date();
  var may20 = new Date('May 20, 2010');
  var day = now < may20 ? 19 : 20;
  var type = item.type;
  var id = item.id;
  var title = item.title;
  var content = ['<div class="infowindow">'];
  var sessions = [];
  var empty = true;

  if (item.type == 'session' && this.sessionItems_) {
    if (day == 19) {
      content.push('<h3>' + title + ' - Wednesday May 19</h3>');
    } else {
      content.push('<h3>' + title + ' - Thursday May 20</h3>');
    }

    for (var i = 0, session; session = this.sessionItems_[i]; i++) {
      if (session.room == item.id && session.sessionDay == day) {
        sessions.push(session);
        empty = false;
      }
    }

    sessions = sessions.sort(this.sortSessions_);

    for (var i = 0, session; session = sessions[i]; i++) {
      content.push('<div class="session"><div class="session-time">' +
        session.sessionTime + '</div><div class="session-title"><a href="' +
        this.SESSION_BASE_ + session.sessionLink + '.html">' +
        session.sessionTitle + '</a></div></div>');
    }
  }

  if (item.type == 'sandbox' && this.sandboxItems_) {
    content.push('<h3>' + item.name + '</h3>');
    content.push('<div class="sandbox">');
    for (var i = 0, sandbox; sandbox = this.sandboxItems_[i]; i++) {
      if (sandbox.pod == item.id) {
        content.push('<div class="sandbox-items"><a href="http://' +
          sandbox.companyUrl + '">' + sandbox.companyName + '</a></div>');
        empty = false;
      }
    }
    content.push('</div>');
  }

  if (item.type == 'officehours' && this.officeHoursItems_) {
    if (day == 19) {
      content.push('<h3>Office Hours - Wednesday May 19</h3>');
    } else {
      content.push('<h3>Office Hours - Thursday May 20</h3>');
    }
    empty = false;

    var items = this.officeHoursItems_[day];

    for (var time in items) {
      content.push('<div class="session"><div class="session-time">' +
        time + '</div><div class="session-products">');
      for (var i = 0, product; product = items[time][i]; i++) {
        content.push('<div>' + product + '</div>');
      }
      content.push('</div></div>');
    }
  }

  if (empty) {
    return;
  }

  content.push('</div>');

  var pos = new google.maps.LatLng(item.lat, item.lng);

  this.infoWindow_.setContent(content.join(''));
  this.infoWindow_.setPosition(pos);
  this.infoWindow_.open(this.map_);

  var that = this;

  google.maps.event.addListenerOnce(this.infoWindow_, 'closeclick', function() {
    that.updateLocationHash(that.currentLevel_);
  });
};


/**
 * Custom sort function for sessions
 * @param {string} a Session a.
 * @param {string} b Session b.
 * @return {number} 1 if greater, -1 if less, 0 if equal.
 * @private
 */
GoogleIo.prototype.sortSessions_ = function(a, b) {
  var aStart = parseInt(a.sessionStart.replace(':', ''), 10);
  var bStart = parseInt(b.sessionStart.replace(':', ''), 10);

  return ((aStart < bStart) ? -1 : ((aStart > bStart) ? 1 : 0));
};


/**
 * Shows the toolbox and add the events to the level buttons.
 * @private
 */
GoogleIo.prototype.enableToolbox_ = function() {
  var toolbox = document.getElementById('toolbox');
  var btnLevel1 = document.getElementById('btn-level1');
  var btnLevel2 = document.getElementById('btn-level2');
  var btnLevel3 = document.getElementById('btn-level3');
  var myLocation = document.getElementById('my-location');
  var that = this;

  toolbox.className = toolbox.className.replace('hide', '');

  google.maps.event.addDomListener(btnLevel1, 'click', function(e) {
    that.handleLevelClick_(e);
  });

  google.maps.event.addDomListener(btnLevel2, 'click', function(e) {
    that.handleLevelClick_(e);
  });

  google.maps.event.addDomListener(btnLevel3, 'click', function(e) {
    that.handleLevelClick_(e);
  });

  if (myLocation) {
    google.maps.event.addDomListener(myLocation, 'click', function(e) {
      that.handleMyLocationClick_(e);
    });
  }
};


/**
 * Handles the click of a level button.
 *
 * @param {Event} e The event.
 * @private
 */
GoogleIo.prototype.handleMyLocationClick_ = function(e) {
  e.stopPropagation();
  e.preventDefault();

  this.map_.setCenter(this.userPosition_);
  this.showLevel(this.userLevel_, true);
};


/**
 * Handles the click of a level button.
 *
 * @param {boolean} show To show or not.
 * @private
 */
GoogleIo.prototype.toggleMyLocationButton_ = function(show) {
  var myLocation = document.getElementById('my-location');

  if (show) {
    myLocation.className = myLocation.className.replace('hide', '');
  } else {
    myLocation.className += ' hide';
  }
};


/**
 * Handles the click of a level button.
 *
 * @param {Event} e The event.
 * @private
 */
GoogleIo.prototype.handleLevelClick_ = function(e) {
  e.stopPropagation();
  e.preventDefault();

  var link = e.currentTarget;
  var level = link.id.replace('btn-level', '');

  this.showLevel(level, true);
};


/**
 * Updates the location hash.
 *
 * @param {string} level The level of the map.
 * @param {string} opt_id The id of the marker (optional).
 */
GoogleIo.prototype.updateLocationHash = function(level, opt_id) {
  document.location.hash = 'level' + level + (opt_id ? ':' + opt_id : '');
};


/**
 * Sets a level to show.
 *
 * @param {string} level The level of the map.
 * @param {boolean?} opt_updateHash Whether to update the hash (optional).
 * @param {boolean?} opt_force Whether to force the update or not (optional).
 */
GoogleIo.prototype.showLevel = function(level, opt_updateHash, opt_force) {
  if (!opt_force && level == this.currentLevel_) {
    // Already on this level so do nothing;
    return;
  }

  var prevLevel = this.currentLevel_;
  var prevLevelBtn = document.getElementById('btn-level' + this.currentLevel_);
  var currentLevelBtn = document.getElementById('btn-level' + level);

  if (prevLevelBtn) {
    prevLevelBtn.className = prevLevelBtn.className.replace(/selected/, '');
  }

  if (currentLevelBtn) {
    currentLevelBtn.className += ' selected';
  }

  this.currentLevel_ = parseInt(level, 10);

  if (this.map_.overlayMapTypes.length != 0) {
    this.map_.overlayMapTypes.removeAt(0);
  }

  if (this.userLocationMarker_) {
    var image;

    if (this.currentLevel_ == this.userLevel_) {
      image = new google.maps.MarkerImage(
        'images/my_location.png',
        new google.maps.Size(14, 14),
        new google.maps.Point(0, 0));
    } else {
      image = new google.maps.MarkerImage(
        'images/my_location_diff.png',
        new google.maps.Size(14, 14),
        new google.maps.Point(0, 0));
    }

    this.userLocationMarker_.setIcon(image);
  }

  this.addMallMapOverlay_();

  if (this.markers_) {
    if (prevLevel) {
      var key = 'LEVEL' + prevLevel;

      for (var i = 0, marker; marker = this.markers_[key][i]; i++) {
        marker.setMap(null);
      }
    }

    for (var i = 0, marker; marker = this.markers_['LEVEL' + level][i]; i++) {
      marker.setMap(this.map_);
    }
  }

  this.closeInfoWindow();
  if (opt_updateHash && !this.hasMapContainer_()) {
    this.updateLocationHash(level);
  }
};


/**
 * Add the mall floor overlay to the map.
 *
 * @private
 */
GoogleIo.prototype.addMallMapOverlay_ = function() {
  var that = this;
  var overlay = new google.maps.ImageMapType({
    getTileUrl: function(coord, zoom) {
      return that.getTileUrl(coord, zoom);
    },
    tileSize: new google.maps.Size(256, 256),
    isPng: true
  });

  this.map_.overlayMapTypes.insertAt(0, overlay);
};


/**
 * Gets the correct tile url for the coordinates and zoom.
 *
 * @param {google.maps.Point} coord The coordinate of the tile.
 * @param {Number} zoom The current zoom level.
 * @return {string} The url to the tile.
 */
GoogleIo.prototype.getTileUrl = function(coord, zoom) {
  // Ensure that the requested resolution exists for this tile layer.
  if (this.MIN_RESOLUTION_ > zoom || zoom > this.MAX_RESOLUTION_) {
    return '';
  }

  // Ensure that the requested tile x,y exists.
  if ((this.RESOLUTION_BOUNDS_[zoom][0][0] > coord.x ||
       coord.x > this.RESOLUTION_BOUNDS_[zoom][0][1]) ||
      (this.RESOLUTION_BOUNDS_[zoom][1][0] > coord.y ||
       coord.y > this.RESOLUTION_BOUNDS_[zoom][1][1])) {
    return '';
  }

  var template = this.TILE_TEMPLATE_URL_;

  if (16 <= zoom && zoom <= 17) {
    template = this.SIMPLE_TILE_TEMPLATE_URL_;
  }

  template = template.replace('{L}', this.currentLevel_).replace('{Z}',
      zoom).replace('{X}', coord.x).replace('{Y}', coord.y);
  return template;
};


/**
 * Sets the users location on the map.
 * @param {string} lat The users lat position.
 * @param {string} lng The users lng position.
 * @param {string} level The level that the user is on.
 * @param {boolean} opt_center To center on the user or not.
 * @param {booelan} opt_showLevel To change the level or not.
 */
GoogleIo.prototype.setUserLocation = function(lat, lng, level, opt_center,
    opt_showLevel) {
  if (!this.userLocationMarker_) {
    var image = new google.maps.MarkerImage(
      'images/my_location.png',
      new google.maps.Size(14, 14),
      new google.maps.Point(0, 0));

    this.userLocationMarker_ = new google.maps.Marker({
      icon: image,
      zIndex: this.userIndex_
    });
  } else {
    if (this.currentLevel_ == level) {
      image = new google.maps.MarkerImage(
        'images/my_location.png',
        new google.maps.Size(14, 14),
        new google.maps.Point(0, 0));
    } else {
      image = new google.maps.MarkerImage(
        'images/my_location_diff.png',
        new google.maps.Size(14, 14),
        new google.maps.Point(0, 0));
    }
    this.userLocationMarker_.setIcon(image);
  }


  var ne = new google.maps.LatLng(37.785001391734994, -122.40050554275513);
  var sw = new google.maps.LatLng(37.779718683356776, -122.40721106529236);
  var buildingBounds = new google.maps.LatLngBounds(sw, ne);

  this.userPosition_ = new google.maps.LatLng(lat, lng);

  if (buildingBounds.contains(this.userPosition_)) {
    this.userLocationMarker_.setPosition(this.userPosition_);
    this.userLocationMarker_.setMap(this.map_);
    this.userLevel_ = level;

    if (opt_showLevel) {
      this.showLevel(level, true);
    }

    if (opt_center) {
      this.map_.setCenter(this.userPosition_);
    }
    this.toggleMyLocationButton_(true);
  } else {
    this.userPosition_ = null;
    this.userLocationMarker_.setMap(null);
    this.toggleMyLocationButton_(false);
  }
};


/**
 * Show the location based on the id.
 *
 * @param {string} id The id of the location to show.
 */
GoogleIo.prototype.showLocationById = function(id) {
  for (var i = 0, level; level = this.LEVELS_[i]; i++) {
    var levelId = 'LEVEL' + level;

    for (var room in this.LOCATIONS[levelId]) {
      if (room == id) {
        var info = this.LOCATIONS[levelId][room];
        var latLng = new google.maps.LatLng(info.lat, info.lng);

        if (this.userPosition_) {
          var bounds = new google.maps.LatLngBounds(latLng, this.userPosition_);
          this.map_.fitBounds(bounds);
        } else {
          this.map_.setCenter(latLng);
          this.map_.setZoom(this.MAX_RESOLUTION_);
        }
        this.showLevel(level, true);
      }
    }
  }
};


/**
 * @type {Object}
 * @private
 */
GoogleIo.prototype.officeHoursItems_ = {
  '19': {
    '12:00pm-2:30pm': [
      'Enterprise',
      'Go Programming Language',
      'Google Project Hosting',
      'Social Web',
      'Google APIs',
      'App Engine'],
    '2:30pm-5:00pm': [
      'Chrome',
      'Closure Compiler',
      'Geo',
      'GWT',
      'Wave',
      'Developer Docs',
      'App Engine']
  },
  '20': {
    '12:00pm-3:00pm': [
      'Chrome',
      'Android',
      'Geo',
      'GWT',
      'Wave',
      'Developer Docs',
      'App Engine'],
    '3:00pm-5:30pm': [
      'Enterprise',
      'Android',
      'Google Project Hosting',
      'Social Web',
      'Google APIs',
      'App Engine']
  }
};

/**
 * @type {Object}
 */
GoogleIo.prototype.LOCATIONS = {
  'LEVEL1': {},
  'LEVEL2': {
   'firesidechatroom': {
      lat: 37.783046918434756,
      lng: -122.40462005138397,
      icon: 'info',
      title: 'Fireside Chats',
      type: 'session'
    },
    '1': {
      lat: 37.78342001060504,
      lng: -122.4041486531496,
      icon: 'media',
      title: 'Room 1',
      type: 'session'
    },
    '2': {
      lat: 37.78331189886305,
      lng: -122.40430690348148,
      icon: 'media',
      title: 'Room 2',
      type: 'session'
    },
    '3': {
      lat: 37.78317304923709,
      lng: -122.40448258817196,
      icon: 'media',
      title: 'Room 3',
      type: 'session'
    },
    '4': {
      lat: 37.78328222110238,
      lng: -122.40380935370922,
      icon: 'media',
      title: 'Room 4',
      type: 'session'
    },
    '5': {
      lat: 37.78314443134288,
      lng: -122.40397699177265,
      icon: 'media',
      title: 'Room 5',
      type: 'session'
    },
    '6': {
      lat: 37.78292608704408,
      lng: -122.4042559415102,
      icon: 'media',
      title: 'Room 6',
      type: 'session'
    },
    '7': {
      lat: 37.7830098210991,
      lng: -122.40380734205246,
      icon: 'media',
      title: 'Room 7',
      type: 'session'
    },
    '8': {
      lat: 37.782828573847965,
      lng: -122.40403197705746 ,
      icon: 'media',
      title: 'Room 8',
      type: 'session'
    },
    '9': {
      lat: 37.78269608288613 ,
      lng: -122.40420296788216,
      icon: 'media',
      title: 'Room 9',
      type: 'session'
    },
   'pressroom': {
     lat: 37.78311899320535,
     lng: -122.4036256223917
    },
    'appengine': {
      lat: 37.78361387539269,
      lng: -122.40358136594296,
      type: 'sandbox',
      icon: 'generic',
      name: 'App Engine'
    },
    'chrome': {
      lat: 37.7832864607833,
      lng: -122.4032662063837,
      type: 'sandbox',
      icon: 'generic',
      name: 'Chrome'
    },
    'enterprise': {
      lat: 37.78332143814089,
      lng: -122.4031562358141,
      type: 'sandbox',
      icon: 'generic',
      name: 'Enterprise'
    },
    'android': {
      lat: 37.78343484945917,
      lng: -122.40348614752293,
      type: 'sandbox',
      icon: 'generic',
      name: 'Android'
    },
    'geo': {
      lat: 37.783660611659144,
      lng: -122.40379594266415,
      type: 'sandbox',
      icon: 'generic',
      name: 'Geo'
    },
    'googleapis': {
      lat: 37.78362245471605,
      lng: -122.40368865430355,
      type: 'sandbox',
      icon: 'generic',
      name: 'Google APIs'
    },
    'gwt': {
      lat: 37.78322286554527,
      lng: -122.40321524441242,
      type: 'sandbox',
      icon: 'generic',
      name: 'GWT'
    },
    'socialweb': {
      lat: 37.783549320520045,
      lng: -122.40365378558636,
      type: 'sandbox',
      icon: 'generic',
      name: 'Social Web'
    },
    'wave': {
      lat: 37.78369982849679,
      lng: -122.4037168174982,
      type: 'sandbox',
      icon: 'generic',
      name: 'Wave'
    },
    'scvngr': {
      lat: 37.78356521926445,
      lng: -122.40382008254528
    },
    'chevvy': {
      lat: 37.78331613854221,
      lng: -122.40365445613861
    }
  },
  'LEVEL3': {
    'keynote': {
      lat: 37.783250423488326,
      lng: -122.40417748689651,
      icon: 'media',
      title: 'Keynote'
    },
    'officehours': {
      lat: 37.78367969012315,
      lng: -122.4036893248558,
      icon: 'generic',
      title: 'Office Hours',
      type: 'officehours'
    },
    'gtug': {
      lat: 37.783293880224164,
      lng: -122.40323670208454
    }
  }
};

// Create the Google Io map object.
var googleIo = new GoogleIo();
