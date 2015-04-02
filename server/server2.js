var io  = require('socket.io');  
var server = io.listen(3000);
var nbPlayers = 0;
var db;
var score = [];
var timer = 0; // A IMPLEMENTER LE TIMER

for (var i=0; i< 10; i++)
  score[i] = 0;

// connection à la base de données
var mysql = require('mysql');
var connection =  mysql.createConnection({
  host : "127.0.0.1",
  user : 'root',
  password: "",
  database : 'paleoWar'
});

connection.connect();


server.sockets.on('connection', function(socket) {  
	console.log("user connected");

 // lien entre le RFID et le joueur
  socket.on('Ready', function(json_Ready){
    var idPlayer = json_Ready.idPlayer;
    console.log(json_Ready);
  	if (findPlayer(idPlayer)){ 		// test si l'idPlayer est pas déjà utilisé
  		socket.emit('ReturnReady', "cet idPlayer est déjà utilisé");
  	}else if (findPseudo(json_Ready.pseudo)){	// test s le pseudo est pas utiliser
  		socket.emit('ReturnReady', "ce pseudo est déjà utilisé");
  	}else{								// retourne l'id RFID du joueur
  		nbPlayers++;

      findRFID(idPlayer, function(rfid){
        socket.emit('ReturnReady', rfid);
      });

      addPlayer(idPlayer, json_Ready.pseudo);
  	}
  });

  // quand les 10 joueurs sont connecté on start la partie
  if (nbPlayers == 10)
  	socket.broadcast.emit('Start');

  // reception d'un tag lu
  socket.on('TAG_Read', function(json_tag){
  	// increment du score de la personne ayant tagger
  	var idPlayer = json_tag.idPlayer;
  	var RFID_Read = json_tag.id_RFID_Read;

  	// test si le RFID est dans la base de données 
  	// en fonction du temps on sait quelle est l'équipe 
  	if (timer <= 5){ // 5 première minutes equipe 1
  		if ((idPlayer <= 5) && (verifyRFID(RFID_Read)))
  			score[idPlayer] += 1;
  	}else if (time <= 10){ // 5 dernière minutes equipe 2	
  		if ((idPlayer > 5) && (verifyRFID(RFID_Read))){
        score[idPlayer] += 1 ;

        var scores = {
          'idPlayer' :idPlayer,
          'score' : score[idPlayer]
        };

  			socket.broadcast.emit('Score', scores);   
        setScore(idPlayer, score[idPlayer]);
  		}
  	}

  });

  // quand le timer fini on stop la partie
  if (timer > 11){
  	socket.broadcast.emit('Stop');
    findWinner(function(data){
      socket.broadcast.emit('winner',data);
    });
    timer = 0;
  }

  // lorsqu'un joueur se déconnecte
  socket.on('disconnect', function () {
    console.log('user disconnected');
  });
});


/*  ============================ Fonction utiles  ============================ */
// récupère l'RFID en fonction de l'id du joueur
function findRFID(id, callback){
  connection.query('SELECT RFID FROM tags WHERE id = '+ parseInt(id) , function(err, rows, fields) {
    if (err) throw err;
    callback(rows[0]["RFID"]);
  });
}

// parcours la liste des RFIDS et vérifie que l'id lu soit dans la BD
function verifyRFID(RFID, callback){
  connection.query('SELECT * FROM tags WHERE RFID = '+ JSON.stringify(RFID) , function(err, rows, fields) {
    if (err) 
      return false;
    else
      return true;
  });
}

// parcours la liste des id dans joueurs pour savoir si l'idJoueur est utilisé
function findPlayer(id, callback){
  connection.query('SELECT * FROM joueurs WHERE id = '+ parseInt(id) , function(err, rows, fields) {
    if (err) 
      return false;
    else
      return true;
  });
}

// parcours les pseudo et vérifie si le pseudo choisi n'est pas déjà utilisé
function findPseudo(pseudo, callback){
  connection.query('SELECT * FROM joueurs WHERE pseudo = '+ JSON.stringify(pseudo) , function(err, rows, fields) {
    if (err)
      return false;
    else
      return true;
  });
}

// parcours la liste des joueurs pour trouver le vainqueur
function findWinner(callback){
  connection.query('SELECT pseudo, MAX('+JSON.stringify(score)+') FROM joueurs', function(err, rows, fields) {
    if (err) throw err;
      var winner = {
        'winner' : rows[0]['pseudo'],
        'score'  : rows[0]['score']
      }

      callback(winner);
  });
}

// ajoute le joueur dans la base de données
function addPlayer(id, pseudo){
  connection.query('INSERT INTO joueurs (id, pseudo, score) VALUES ('+parseInt(id)+',' + 
    JSON.stringify(pseudo)+',0)' , function(err, rows, fields) {
    if (err) throw err;
  });
}

// met à jour le score dans la bd
function setScore(id, score, callback){
  connection.query('UPDATE joueurs SET score = '+parseInt(score)+' WHERE id ='+parseInt(id) , function(err, rows, fields) {
    if (err) throw err;
  });
}