window.Reads = Ember.Application.create();

Reads.ApplicationAdapter = DS.FixtureAdapter.extend();

Reads.Router.map(function() {
  this.resource('reads', { path: '/' }),
  this.resource('read', { path: '/read_id' });
});

Reads.ReadsRoute = Ember.Route.extend({
  model: function() {
    return this.store.find('read');
  }
});

Reads.ReadRoute = Ember.Route.extend({
  model: function(params) {
    return this.store.find('read', params.id);
  }
});

Reads.Read = DS.Model.extend({
  name: DS.attr('string'),
  x: DS.attr('number'),
  y: DS.attr('number'),
  w: DS.attr('number'),
  h: DS.attr('number')
});

Reads.Read.FIXTURES = [
 {
   id: 1,
   name: 'Read 1',
   x: 5,
   y: 5,
   w: 10,
   h: 2
 },
 {
   id: 2,
   name: 'Read 2',
   x: 10,
   y: 10,
   w: 15,
   h: 2
 },
 {
   id: 3,
   name: 'Read 3',
   x: 15,
   y: 15,
   w: 20,
   h: 2
 }
];

Ember.Handlebars.helper('readBox', function(read) {
  var x = read.get('x');
  var y = read.get('y');
  var w = read.get('w');
  var h = read.get('h');
  return new Ember.Handlebars.SafeString('<rect x=' + x + ' y=' + y + ' width=' + w + ' height=' + h + ' style="fill:blue"/>');
}, 'x', 'y', 'w', 'h');