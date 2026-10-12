const accessToken = "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VybmFtZSI6InRlc3RVc2VybmFtZSIsImlkIjo4LCJyb2xlIjoiQ0xJRU5UIiwiaWF0IjoxNzkxNzI3ODQzLCJleHAiOjE3OTE3Mjg0NDN9.kuaUt1VHGA7ZGn_R49ZCZa23XuSy9G7gETQ6A_FBbmOT-z2Prma7-uYMQK2pkaVxxBJ_oqRUr-N9gWpz7mZk1A"
const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8080/ws/chat',
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
});

stompClient.onConnect = (frame) => {
    setConnected(true);
    console.log('Connected: ' + frame);
    stompClient.subscribe('/topic/chat/1', (greeting) => {
        console.log(greeting)
        const res = JSON.parse(greeting.body);
        console.log(res);
        showGreeting(res.senderId);
        showGreeting(res.senderName);
        showGreeting(res.content);
        showGreeting(res.sentAt);
        showGreeting(res.type);
    });
};

stompClient.onWebSocketError = (error) => {
    console.error('Error with websocket', error);
};

stompClient.onStompError = (frame) => {
    console.error('Broker reported error: ' + frame.headers['message']);
    console.error('Additional details: ' + frame.body);
};

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);
    if (connected) {
        $("#conversation").show();
    }
    else {
        $("#conversation").hide();
    }
    $("#greetings").html("");
}

function connect() {
    stompClient.activate();
}

function disconnect() {
    stompClient.deactivate();
    setConnected(false);
    console.log("Disconnected");
}

function sendName() {
    const abc = {
        "senderId": parseInt($("#userId").val(), 10),
        "content": $("#content").val()
    }
    console.log(JSON.stringify(abc))
    stompClient.publish({
        destination: "/app/chat/1",
        body: JSON.stringify(abc)
    });
}

function showGreeting(message) {
    $("#greetings").append("<tr><td>" + message + "</td></tr>");
}

$(function () {
    $("form").on('submit', (e) => e.preventDefault());
    $( "#connect" ).click(() => connect());
    $( "#disconnect" ).click(() => disconnect());
    $( "#send" ).click(() => sendName());
});

