#include <jni.h>
#include <android/log.h>
#include <string>

#include "cshogi.h"

#define LOG_TAG "AndroShogi"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * MoveConverter
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_org_androshogi_shogi_Move_nativeToUSI(JNIEnv *env, jclass obj, jint move) {
    const std::string result = __to_usi(move);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_androshogi_shogi_Move_nativeToCSA(JNIEnv *env, jclass obj, jint move) {
    const std::string result = __to_csa(move);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Move_nativeFromUSI(JNIEnv *env, jclass obj, jlong ptr, jstring usi) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    const char *usiChars = env->GetStringUTFChars(usi, nullptr);
    if (usiChars == nullptr) {
        return 0;
    }
    const std::string usiString(usiChars);
    env->ReleaseStringUTFChars(usi, usiChars);
    return board->move_from_usi(usiString);
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Move_nativeFromCSA(JNIEnv *env, jclass obj, jlong ptr, jstring csa) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    const char *csaChars = env->GetStringUTFChars(csa, nullptr);
    if (csaChars == nullptr) {
        return 0;
    }
    const std::string csaString(csaChars);
    env->ReleaseStringUTFChars(csa, csaChars);
    return board->move_from_csa(csaString);
}

/*
 * Board
 */
extern "C"
JNIEXPORT jlong JNICALL
Java_org_androshogi_shogi_Board_nativeCreate(JNIEnv *env, jobject obj) {
    return reinterpret_cast<jlong>(new __Board());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_androshogi_shogi_Board_nativeCreateFromSFEN(JNIEnv *env, jobject obj, jstring sfen) {
    const char *sfenChars = env->GetStringUTFChars(sfen, nullptr);
    const __Board *board = new __Board(std::string(sfenChars));
    env->ReleaseStringUTFChars(sfen, sfenChars);
    return reinterpret_cast<jlong>(board);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_Board_nativeSetSFEN(JNIEnv *env, jobject obj, jlong ptr, jstring sfen) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    const char *sfenChars = env->GetStringUTFChars(sfen, nullptr);
    board->set(std::string(sfenChars));
    env->ReleaseStringUTFChars(sfen,sfenChars);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_androshogi_shogi_Board_nativeGetSFEN(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    const std::string sfen = board->toSFEN();
    return env->NewStringUTF(sfen.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_Board_nativeInitialize(JNIEnv *env, jclass obj) {
    initTable();
    Position::initZobrist();
    HuffmanCodedPos_init();
    PackedSfen_init();
    Book_init();
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_Board_nativeDestroy(JNIEnv *env, jobject obj, jlong ptr) {
    delete reinterpret_cast<__Board *>(ptr);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_androshogi_shogi_Board_nativeCopy(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return reinterpret_cast<jlong>(new __Board(*board));
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_Board_nativeReset(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    board->reset();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_androshogi_shogi_Board_nativeDump(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    const std::string dump = board->dump();
    return env->NewStringUTF(dump.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Board_nativePiece(JNIEnv *env, jobject obj, jlong ptr, jint sq) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return board->piece(sq);
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_org_androshogi_shogi_Board_nativePieces(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    const std::vector<int> pieces = board->pieces();
    jintArray result = env->NewIntArray((int)pieces.size());
    if (result == nullptr) {
        return nullptr;
    }

    env->SetIntArrayRegion(result, 0, (int)pieces.size(), pieces.data());
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_Board_nativePush(JNIEnv *env, jobject obj, jlong ptr, jint move) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    board->push(move);
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Board_nativePop(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return board->pop();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Board_nativePeek(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return board->peek();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Board_nativeTurn(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return board->turn();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Board_nativeGetMove(JNIEnv *env, jobject obj, jlong ptr, jint squareFrom, jint squareTo, jboolean promotion) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return board->move(squareFrom, squareTo, promotion);
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_Board_nativeGetDropMove(JNIEnv *env, jobject obj, jlong ptr, jint squareTo, jint pieceType) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return board->drop_move(squareTo, pieceType);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_androshogi_shogi_Board_nativeIsLegal(JNIEnv *env, jobject obj, jlong ptr, jint move) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return board->moveIsLegal(move);
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_org_androshogi_shogi_Board_nativePiecesInHand(JNIEnv *env, jobject obj, jlong ptr, jint color) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    const std::vector<int> pieces = board->pieces_in_hand(color);
    jintArray result = env->NewIntArray((int)pieces.size());
    if (result == nullptr) {
        return nullptr;
    }

    env->SetIntArrayRegion(result, 0, (int)pieces.size(), pieces.data());
    return result;
}

/*
 * LegalMoveList
 */
extern "C"
JNIEXPORT jlong JNICALL
Java_org_androshogi_shogi_LegalMoveList_nativeCreate(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return reinterpret_cast<jlong>(new __LegalMoveList(*board));
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_LegalMoveList_nativeDestroy(JNIEnv *env, jobject obj, jlong ptr) {
    delete reinterpret_cast<__LegalMoveList *>(ptr);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_androshogi_shogi_LegalMoveList_nativeEnd(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__LegalMoveList *>(ptr);
    return moveList->end();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_LegalMoveList_nativeMove(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__LegalMoveList *>(ptr);
    return moveList->move();
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_LegalMoveList_nativeNext(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__LegalMoveList *>(ptr);
    moveList->next();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_LegalMoveList_nativeSize(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__LegalMoveList *>(ptr);
    return moveList->size();
}

/*
 * PseudoLegalMoveList
 */
extern "C"
JNIEXPORT jlong JNICALL
Java_org_androshogi_shogi_PseudoLegalMoveList_nativeCreate(JNIEnv *env, jobject obj, jlong ptr) {
    auto *board = reinterpret_cast<__Board *>(ptr);
    return reinterpret_cast<jlong>(new __PseudoLegalMoveList(*board));
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_PseudoLegalMoveList_nativeDestroy(JNIEnv *env, jobject obj, jlong ptr) {
    delete reinterpret_cast<__PseudoLegalMoveList *>(ptr);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_androshogi_shogi_PseudoLegalMoveList_nativeEnd(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__PseudoLegalMoveList *>(ptr);
    return moveList->end();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_PseudoLegalMoveList_nativeMove(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__PseudoLegalMoveList *>(ptr);
    return moveList->move();
}

extern "C"
JNIEXPORT void JNICALL
Java_org_androshogi_shogi_PseudoLegalMoveList_nativeNext(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__PseudoLegalMoveList *>(ptr);
    moveList->next();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_androshogi_shogi_PseudoLegalMoveList_nativeSize(JNIEnv *env, jobject obj, jlong ptr) {
    auto *moveList = reinterpret_cast<__PseudoLegalMoveList *>(ptr);
    return moveList->size();
}
