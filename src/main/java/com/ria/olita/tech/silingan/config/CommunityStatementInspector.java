package com.ria.olita.tech.silingan.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;

@Component
public class CommunityStatementInspector implements StatementInspector {

	@Override
	public String inspect(String sql) {

		if (UserContextHolder.isPlatformAdmin()) {
			return sql;
		}

		String normalized = sql.toLowerCase()
			.trim();

		if (!normalized.startsWith("select")) {
			return sql;
		}

		if (!isCommunityScopedTable(normalized)) {
			return sql;
		}

		if (hasCommunityFilter(normalized)) {
			return sql;
		}

		// BLOCK unsafe query
		throw new ValidationException(
			"Unsafe query detected (missing community filter): " + sql
		);
	}

	private boolean isCommunityScopedTable(String sql) {
		return sql.contains("announcements")
									|| sql.contains("issues")
									|| sql.contains("media")
									|| sql.contains("community_admin_invitations");
	}

	private boolean hasCommunityFilter(String sql) {
		return sql.contains("community_id");
	}
}

