package distributed.systems.das;

/**
 * Different request types for the
 * nodes to send to the server.
 * 
 */
public enum MessageRequest {
	spawnUnit, getUnit, moveUnit, putUnit, removeUnit, getType, dealDamage, healDamage, requestBFList, replyBFList, addBF, spawnAck, SyncAction, SyncActionResponse, SyncActionConfirm, gameState
}


