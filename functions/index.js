const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");

initializeApp();

const DATABASE_ID = "ai-studio-android-esosa-be1f2445-fec9-4893-90b3-912081147f74";
const db = getFirestore(DATABASE_ID);

async function collectTokensForUsers(uids) {
  const uniqueUids = [...new Set(uids.filter((id) => typeof id === "string" && id.length > 0))];
  if (uniqueUids.length === 0) return { tokens: [], uidByToken: new Map() };

  const docs = await Promise.all(
    uniqueUids.map((uid) => db.collection("users").doc(uid).get())
  );

  const tokens = [];
  const uidByToken = new Map();
  for (const snap of docs) {
    if (!snap.exists) continue;
    const userData = snap.data() || {};
    if (userData.notificationsEnabled === false) continue;

    const candidates = new Set();
    if (typeof userData.fcmToken === "string" && userData.fcmToken.trim()) {
      candidates.add(userData.fcmToken.trim());
    }
    if (Array.isArray(userData.fcmTokens)) {
      for (const token of userData.fcmTokens) {
        if (typeof token === "string" && token.trim()) {
          candidates.add(token.trim());
        }
      }
    }
    for (const token of candidates) {
      if (!uidByToken.has(token)) {
        tokens.push(token);
        uidByToken.set(token, snap.id);
      }
    }
  }
  return { tokens, uidByToken };
}

async function sendMulticastWithPrune(tokens, uidByToken, notification, data) {
  if (!tokens.length) return;
  const response = await getMessaging().sendEachForMulticast({
    tokens,
    notification,
    data,
    android: {
      priority: "high",
      notification: {
        channelId: data.type === "new_message" ? "esosa_messages" : "esosa_social"
      }
    }
  });

  const staleUpdates = [];
  response.responses.forEach((res, idx) => {
    if (!res.success) {
      const code = res.error?.code || "";
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        const badToken = tokens[idx];
        const ownerUid = uidByToken.get(badToken);
        if (ownerUid) {
          staleUpdates.push(
            db.collection("users").doc(ownerUid).update({
              fcmTokens: FieldValue.arrayRemove(badToken)
            }).catch(() => {})
          );
        }
      }
    }
  });
  await Promise.all(staleUpdates);
}

exports.onNewRoomMessagePush = onDocumentCreated(
  {
    document: "rooms/{roomId}/messages/{messageId}",
    database: DATABASE_ID
  },
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    const roomId = event.params.roomId;
    const senderId = message.senderId || "";
    const senderName = message.senderName || "Someone";
    const memberUids = Array.isArray(message.memberUids) ? message.memberUids : [];
    const recipientUids = memberUids.filter((uid) => uid !== senderId);
    if (!recipientUids.length) return;

    let roomTitle = "Esosa Chat";
    const roomSnap = await db.collection("rooms").doc(roomId).get();
    if (roomSnap.exists && typeof roomSnap.data()?.title === "string") {
      roomTitle = roomSnap.data().title;
    }

    const bodyText = (message.text || "").trim() ||
      (message.imageUrl ? "Sent a photo" : "Sent a new message");

    const { tokens, uidByToken } = await collectTokensForUsers(recipientUids);
    await sendMulticastWithPrune(
      tokens,
      uidByToken,
      {
        title: `${senderName} in ${roomTitle}`,
        body: bodyText.slice(0, 180)
      },
      {
        type: "new_message",
        roomId: String(roomId),
        roomTitle: String(roomTitle),
        senderName: String(senderName),
        messageText: bodyText.slice(0, 180)
      }
    );
  }
);

exports.onPostLikedPush = onDocumentUpdated(
  {
    document: "posts/{postId}",
    database: DATABASE_ID
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    const authorId = after.authorId;
    if (!authorId) return;

    const prevLikes = Array.isArray(before.likedByUids) ? before.likedByUids : [];
    const nextLikes = Array.isArray(after.likedByUids) ? after.likedByUids : [];
    const addedLikers = nextLikes.filter((uid) => uid !== authorId && !prevLikes.includes(uid));
    if (!addedLikers.length) return;

    const latestLikerUid = addedLikers[addedLikers.length - 1];
    let likerUsername = "Someone";
    const likerDoc = await db.collection("users").doc(latestLikerUid).get();
    if (likerDoc.exists) {
      const data = likerDoc.data() || {};
      likerUsername = data.username || data.displayName || "Someone";
    }

    const postPreview = (after.caption || "").trim().slice(0, 90);
    const body = postPreview
      ? `@${likerUsername} liked your post: "${postPreview}"`
      : `@${likerUsername} liked your post`;

    const { tokens, uidByToken } = await collectTokensForUsers([authorId]);
    await sendMulticastWithPrune(
      tokens,
      uidByToken,
      {
        title: "New like on Esosa",
        body
      },
      {
        type: "post_like",
        postId: String(event.params.postId),
        likerUsername: String(likerUsername),
        postPreview
      }
    );
  }
);

exports.onStoryReplyPush = onDocumentCreated(
  {
    document: "stories/{storyId}/replies/{replyId}",
    database: DATABASE_ID
  },
  async (event) => {
    const reply = event.data?.data();
    if (!reply) return;

    const storyAuthorId = reply.storyAuthorId || "";
    const senderId = reply.authorId || "";
    if (!storyAuthorId || storyAuthorId === senderId) return;

    const senderUsername = reply.authorUsername || "Someone";
    const replyText = (reply.text || "").trim().slice(0, 180) || "Replied to your story";

    const { tokens, uidByToken } = await collectTokensForUsers([storyAuthorId]);
    await sendMulticastWithPrune(
      tokens,
      uidByToken,
      {
        title: `@${senderUsername} replied to your story`,
        body: replyText
      },
      {
        type: "story_reply",
        storyId: String(event.params.storyId),
        senderUsername: String(senderUsername),
        replyText
      }
    );
  }
);
