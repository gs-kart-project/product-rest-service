# Product Catalog Service

This repository contains the **Product Catalog microservice** for the GS Kart ecommerce platform. This service is part of the Capstone project developed at Scaler Academy.

## 📋 Project Overview

The Product Catalog Service is a core microservice responsible for managing product listings, categories, and search functionality in the GS Kart ecommerce ecosystem. It provides RESTful APIs for browsing products by categories, retrieving detailed product information, and searching products by keywords.

### Key Features

- **Product Management:** Full CRUD operations for products
- **Category Management:** Complete category management with product associations
- **Product Browsing:** Browse products by category
- **Product Search:** Advanced search with pagination and sorting support
- **Security:** Role-based access control (Admin/Developer roles)
- **Database:** MySQL for reliable data persistence
- **API-First Design:** RESTful endpoints following REST conventions

## 🏗️ Architecture

### Technology Stack

- **Framework:** Spring Boot 4.0.3
- **Language:** Java 25
- **Database:** MySQL
- **Build Tool:** Maven
- **ORM:** Spring Data JPA / Hibernate
- **Security:** Spring Security with role-based authorization
- **API Format:** REST with JSON
- **Dependency Injection:** Spring IoC Container

### Project Structure

```
src/main/java/com/gskart/product/
├── controllers/          # REST API endpoints
├── services/            # Business logic layer
├── entities/            # JPA entities
├── DTOs/               # Data Transfer Objects
├── mappers/            # Entity-DTO conversion
├── repositories/       # Spring Data JPA repositories
├── exceptions/         # Custom exceptions
├── security/           # Security configuration & filters
└── fakestore/          # Integration with external API
```

### Database Schema

**Key Entities:**
- **Product:** Core product information (name, description, price, image URL, status)
- **Category:** Product categories (name, description, image URL)
- **Relationship:** Many-to-One (Products → Category)

## 🔌 REST API Endpoints

### Product Endpoints

| Endpoint | Method | Purpose | Auth Required | Status Code |
|----------|--------|---------|---------------|-------------|
| `/products` | GET | Get all products | No | 200, 204 |
| `/products/{id}` | GET | Get product by ID | No | 200, 204 |
| `/products/category/{categoryId}` | GET | Get products by category | No | 200, 204 |
| `/products/search` | GET | Search products by keyword | No | 200, 204 |
| `/products/category/{categoryId}` | POST | Create new product | No | 201, 400, 500 |
| `/products/{id}` | PUT | Update product | Yes* | 200, 400, 404 |
| `/products/{id}` | DELETE | Delete product | Yes* | 200, 404 |

### Category Endpoints

| Endpoint | Method | Purpose | Auth Required | Status Code |
|----------|--------|---------|---------------|-------------|
| `/categories` | GET | Get all categories | No | 200, 204 |
| `/categories/{id}` | GET | Get category by ID | No | 200, 204 |
| `/categories` | POST | Create new category | Yes* | 201, 400 |
| `/categories/{id}` | PUT | Update category | Yes* | 201, 400 |
| `/categories/{id}` | DELETE | Delete category | Yes* | 200, 404 |

**Auth Required:** * Requires 'Developer' or 'Admin' role

### Query Parameters

**Search Endpoint (`GET /products/search`):**
- `query` (required) - Search keyword
- `page` (optional, default: 0) - Page number for pagination
- `size` (optional, default: 10) - Number of results per page
- `sort` (optional, default: "name:asc") - Sort field and direction (format: "field:direction")

**Example:**
```
GET /products/search?query=laptop&page=0&size=20&sort=price:desc
```

## 📦 API Response Format

### Product DTO
```json
{
  "id": 1,
  "name": "Product Name",
  "description": "Product description",
  "imageUrl": "https://example.com/image.jpg",
  "price": 99.99
}
```

### Category DTO
```json
{
  "id": 1,
  "name": "Category Name",
  "description": "Category description",
  "imageUrl": "https://example.com/image.jpg"
}
```

### Search Response
```json
{
  "products": [
    {
      "id": 1,
      "name": "Product Name",
      "description": "Product description",
      "imageUrl": "https://example.com/image.jpg",
      "price": 99.99
    }
  ],
  "currentPage": 0,
  "totalItems": 50,
  "totalPages": 5
}
```

## 🚀 Getting Started

### Prerequisites

- Java 25 or higher
- Maven 3.8.0 or higher
- MySQL 8.0 or higher

### Build & Run

```bash
# Clone the repository
git clone <repository-url>
cd gskart/product

# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

### Database Setup

Flyway migrations are automatically applied on application startup. The database will be initialized with the schema defined in:
```
src/main/resources/db/migration/productDb/V1__DBInitialize.sql
```

## 📋 Implementation Status

### ✅ Completed Features

- [x] Spring Boot 4.0.3 upgrade with proper Lombok configuration
- [x] Product CRUD operations
- [x] Category CRUD operations
- [x] Browse products by category
- [x] Search products with pagination and sorting
- [x] Role-based access control (Admin/Developer)
- [x] Exception handling with custom exceptions
- [x] DTO mapping with ModelMapper
- [x] MySQL database integration
- [x] Flyway database migrations
- [x] Spring Security integration

### 📊 PRD Compliance

**Requirement 2.1 - Browsing:** ✅ 100% Complete
- Browse all products
- Browse products by category
- Browse categories

**Requirement 2.2 - Product Details:** ✅ 100% Complete
- Get detailed product information
- Product images, descriptions, specifications available

**Requirement 2.3 - Search:** ✅ 100% Complete
- Search products by keywords
- Pagination support
- Sorting support

**Overall PRD Compliance:** ✅ **100%** (Elasticsearch excluded per project requirements)

## 🧪 Testing

### Example API Calls

**Get all products:**
```bash
curl http://localhost:8080/products
```

**Get products by category:**
```bash
curl http://localhost:8080/products/category/1
```

**Search products:**
```bash
curl "http://localhost:8080/products/search?query=laptop&page=0&size=10&sort=price:desc"
```

**Get product by ID:**
```bash
curl http://localhost:8080/products/1
```

## 📝 Exception Handling

The service handles the following exceptions gracefully:

- **ProductNotFoundException** - When a requested product is not found (HTTP 404)
- **CategoryNotFoundException** - When a requested category is not found (HTTP 404)
- **ProductAddFailedException** - When product creation fails (HTTP 400)

## 🔐 Security

- Spring Security with role-based authorization
- Endpoints requiring authentication use `@PreAuthorize("hasAnyAuthority('Developer','Admin')")`
- Authentication tokens are validated through the API Gateway

## 📚 Documentation

- **IMPLEMENTATION_SUMMARY.md** - Details about Spring Boot upgrade and endpoint implementation
- **MISSING_ENDPOINTS_IMPLEMENTATION.md** - Comprehensive guide for search and category filtering endpoints
- **IMPLEMENTATION_COMPLETE.md** - Final implementation status

## 🛠️ Development

### Adding New Features

1. Create entity in `entities/` package
2. Create repository extending `CrudRepository`
3. Create DTO in `DTOs/` package
4. Create mapper in `mappers/` package
5. Create service in `services/` package
6. Create controller in `controllers/` package
7. Write tests in `src/test/java/`

### Build Commands

```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Build WAR file
./mvnw clean package

# Run in development mode
./mvnw spring-boot:run
```

## 📌 Dependencies

Key Maven dependencies:
- spring-boot-starter-data-jpa
- spring-boot-starter-security
- spring-boot-starter-web
- mysql-connector-j
- lombok
- flyway-mysql

## 🤝 Contributing

This is a Scaler Academy Capstone project. Please follow the existing code conventions and commit messages.

## 📄 License

Part of the GS Kart Capstone Project - Scaler Academy

## 📞 Support

For issues or questions, please refer to the project documentation or contact the development team.

---

**Last Updated:** March 1, 2026
**Spring Boot Version:** 4.0.3
**Java Version:** 25
**Status:** ✅ Production Ready
