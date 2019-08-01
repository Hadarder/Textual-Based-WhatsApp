# Textual-Based WhatsApp

The project specifications can be found in: https://www.cs.bgu.ac.il/~majeek/atd/192/assignments/2/

## Our implementation:

In our system there are 3 types of actors:
1. Manger Actor - Acts as the managing server. Responsible for all chat server side features such as user and group operations.
2. Client IO actor - Responsible for initial processing of user input from keyboard. This actor builds commands according to the input provided and passes them to the communication actor. Also, this actor is responsible for printing messages to the user screen.
3. Client communication actor - Responsible for communicating with different actors, and building response commands according to their responses. This actor is used as a pipe between between the IO actor and the manager actor/other communication actors. The main reason for using this actor is our aim to avoid blocking the user when performing blocking operations. For example, When using \'91ask\'92 to get server response.
\
We created the Command abstract class, in order to represent all different commands in the system. Each command is translated into a class instance which extends the Command abstract class.
Command classes in our system are the data structures passed between the different actor. Each actor reacts according to the Command type it received.
There are other classes passed between actors:
Data - containing text/file messages sent to/from users.
Message - sent to IO Actor in request for processing data or printing data.
Response - used to express an actor response for different requests.

A user in the system is represented by the User class, which contains:
- User name.
- Communication actor reference, to be used when interacting with the user.

A group in the system is represented by the Group class, which contains:
- Group name
- Group members HashMap, containing the username and state of each member in the group.
- A router object for communicating with all of the group members.
- Muted members HashMap, containing the username, mute duration and cancel function for each muted user.
\
The managing server holds:
- A HashMap containing all connected users in the system.
- A HashMap containing all the groups in the system.
- The scheduler object of the system.

The IO actor holds the communication actor ActorRef.
The Communication actor holds:
- The manager actor ActorRef.
- The client IO actor ActorRef.
- The user object.
- The inviter ActorRef.

How does it work?
The client Main function reads the user input and sends it to the IO actor for initial processing.
All user validations are performed by the manager actor, which informs the communication actor about success/failure.
1 to 1 chat:
The communication actor of the source user requests the target user information from the manager actor. If all validations pass, the manager responds with the target user information and the source communication actor approaches the target communication actor directly.
1 to many chat:
When a user wishes to send a broadcast message to a group, is The source communication actor passes this message to the manager. The manager performs validations, and upon success, broadcasts the message to all of the group communication actors, using the group router.
In certain group operations, the source also needs to send notification messages to another actor, supplied by the manager.
We added a validation to some of the group operations, forbidding a group member to perform certain operations over the group admin. For example: mute user, remove user.

Project structure:
The extracted folder contains:
- A Server folder, containing server implementation.
- A Client folder, containing client implementation.
- An src folder, containing shared classes.
- A pox.xml file.
- This README.
