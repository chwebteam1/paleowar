var io  = require('socket.io');  
var server = io.listen(3000);
var nbPlayers = 0;
var db;
var score = [];
var timer = 0; 
var interval = null;
var totalPlayers = 1;//10;
var timeRound = 1;//10/2;

for (var i=0; i< totalPlayers; i++)
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

    // test si l'idPlayer est pas déjà utilisé
  /*  findPlayer(idPlayer, function(test){
      if (test)
        socket.emit('ReturnReady', "cet idPlayer est déjà utilisé");
      else
        // test si le pseudo est pas utiliser
        findPseudo(json_Ready.pseudo,function(test){
          if (test)
            socket.emit('ReturnReady', "ce pseudo est déjà utilisé");
          else{*/ 
            // retourne l'id RFID du joueur

            findRFID(idPlayer, function(rfid){
              socket.emit('ReturnReady', rfid);
            });

            nbPlayers++;

            addPlayer(idPlayer, json_Ready.pseudo);

            // attend 1 secondes pour envoyer le start
            setTimeout(function(){
              // quand les 10 joueurs sont connecté on start la partie
              if (nbPlayers == totalPlayers){
                socket.emit('Start');
                socket.broadcast.emit('Start');
                getScores(function(scores){
                  console.log(scores);
                  socket.emit('Score', scores);  
                  socket.broadcast.emit('Score', scores);  
                });

                // lance le timer et verifie les tags
                interval = setInterval(function(){
                   inc_timer();
                   checkTags(socket);
                   endTimer(socket);
                },1000);
              }
            }, 1000);
            
         // }
       // }); 
  //  });
  });

  // lorsqu'un joueur se déconnecte
  socket.on('disconnect', function () {
    console.log('user disconnected');
  });
});


/*  ============================ Fonction utiles  ============================ */
// fonction permettant d'implementer un timer
var secondes = 0;
function inc_timer(){
  secondes++;
  if (secondes == 60){
    timer++;
    secondes = 0;
  }
}

// quand le timer fini on stop la partie
function endTimer(socket){
   if (timer > (timeRound * 2)){
    clearInterval(interval);
    timer = 0;
    socket.broadcast.emit('Stop');

    // envoi le winner
    findWinner(function(data){
      socket.emit('winner',data);
      socket.broadcast.emit('winner',data);
    });

    console.log("end of game");
  }
}


// reception d'un tag lu
function checkTags(socket){
socket.on('TAG_Read', function(json_tag){

  // increment du score de la personne ayant tagger
  var idPlayer = json_tag.idPlayer;
  var RFID_Read = json_tag.RFID_Read;
  var equipe = 1;

  // test si le RFID est dans la base de données 
  // en fonction du temps on sait quelle est l'équipe 
  if (timer <= timeRound){ // 5 première minutes equipe 1
    if (idPlayer <= (totalPlayers/2)) 
      equipe = 1;
    else
      return;
  }else if (timer <= (timeRound * 2)){ // 5 dernière minutes equipe 2 
    if (idPlayer > (totalPlayers/2))
      equipe = 2;
    else
      return;
  }

  verifyRFID(RFID_Read, function(bool,id){
    if(bool){
      // augmente le score que si c'est un ennemi
     /* if ((equipe == 1) && (id > 5)) 
        score[tagger] += 1;
       if ((equipe == 2) && (id <= 5))
        score[tagger] += 1; */
      score[idPlayer] += 1;
    } 
   });

  setScore(idPlayer, score[idPlayer]); // update le score dans la base de données

  // envoi les scores de chaque joueurs 
  getScores(function(scores){
    socket.emit('Score', scores);  
    socket.broadcast.emit('Score', scores);  
  });
});
}

// récupère l'RFID en fonction de l'id du joueur
function findRFID(id, callback){
  connection.query('SELECT RFID FROM tags WHERE id = '+ parseInt(id) , function(err, rows, fields) {
    if (err) throw err;
    callback(rows[0]["RFID"]);
  });
}

// parcours la liste des RFIDS et vérifie que l'id lu soit dans la BD
function verifyRFID(RFID, equipe, callback){
  connection.query('SELECT id FROM tags t WHERE t.RFID = '+ JSON.stringify(RFID), function(err, rows, fields) {
    if (err) throw err;
    if ( rows[0] == null)
      callback(false,null);
    else 
      callback(true,id);
  });
}

// parcours la liste des id dans joueurs pour savoir si l'idJoueur est utilisé
function findPlayer(id, callback){
  connection.query('SELECT * FROM joueurs WHERE id = '+ parseInt(id) , function(err, rows, fields) {
    if (err) throw err;
    if ( rows[0] == null)
      callback(false);
    else 
      callback(true);
  });
}

// parcours les pseudo et vérifie si le pseudo choisi n'est pas déjà utilisé
function findPseudo(pseudo, callback){
  connection.query('SELECT * FROM joueurs WHERE pseudo = '+ JSON.stringify(pseudo) , function(err, rows, fields) {
    if (err) throw err;
    if (rows[0] == null)
      callback(false);
    else 
      callback(true);
  });
}

// parcours la liste des joueurs pour trouver le vainqueur
function findWinner(callback){
  connection.query('SELECT pseudo, MAX(score) FROM joueurs', function(err, rows, fields) {
    if (err) throw err;
      var winner = {
        'winner' : rows[0]['pseudo'],
        'score'  : rows[0]['score']
      }

      callback(winner);
  });
}

// parcours la liste des joueurs pour trouver le vainqueur
function getScores(callback){
  connection.query('SELECT * FROM joueurs', function(err, rows, fields) {
    if (err) throw err;
      callback(rows);
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