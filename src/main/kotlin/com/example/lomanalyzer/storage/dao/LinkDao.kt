package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class LinkDao(private val db: Database) {
    fun linkSessionCommunity(sessionId: Int, communityId: Int) = transaction(db) {
        SessionCommunities.insert {
            it[SessionCommunities.sessionId] = sessionId
            it[SessionCommunities.communityId] = communityId
        }
    }

    fun linkSessionAuthor(sessionId: Int, authorId: Int, role: String? = null) = transaction(db) {
        SessionAuthors.insert {
            it[SessionAuthors.sessionId] = sessionId
            it[SessionAuthors.authorId] = authorId
            it[SessionAuthors.role] = role
        }
    }

    fun linkAnomalyAuthor(anomalyId: Int, authorId: Int) = transaction(db) {
        AnomalyAuthorLinks.insert {
            it[AnomalyAuthorLinks.anomalyId] = anomalyId
            it[AnomalyAuthorLinks.authorId] = authorId
        }
    }

    fun linkAnomalyPost(anomalyId: Int, postId: Int) = transaction(db) {
        AnomalyPostLinks.insert {
            it[AnomalyPostLinks.anomalyId] = anomalyId
            it[AnomalyPostLinks.postId] = postId
        }
    }

    fun linkRiskAnomaly(riskId: Int, anomalyId: Int) = transaction(db) {
        RiskAnomalyLinks.insert {
            it[RiskAnomalyLinks.riskId] = riskId
            it[RiskAnomalyLinks.anomalyId] = anomalyId
        }
    }

    fun getCommunitiesForSession(sessionId: Int): List<ResultRow> = transaction(db) {
        SessionCommunities.selectAll()
            .where { SessionCommunities.sessionId eq sessionId }
            .toList()
    }

    fun getAuthorsForSession(sessionId: Int): List<ResultRow> = transaction(db) {
        SessionAuthors.selectAll()
            .where { SessionAuthors.sessionId eq sessionId }
            .toList()
    }
}
